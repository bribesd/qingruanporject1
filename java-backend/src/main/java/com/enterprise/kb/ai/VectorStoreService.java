package com.enterprise.kb.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 模块3：向量存储与检索。优先使用 Chroma REST API（/api/v1）；
 * Chroma 不可用时自动降级为进程内内存余弦检索（重启丢失，由启动重建索引补偿）。
 * metadata 携带 knowledge_id/title/category_id/embed_scheme，检索按 embed_scheme 过滤，
 * 避免不同向量化方案（远程 SBERT / 本地降级）的向量混入同一检索空间。
 */
@Service
public class VectorStoreService {

    private static final Logger log = LoggerFactory.getLogger(VectorStoreService.class);

    /** Chroma 失败后的重试间隔（毫秒） */
    private static final long CHROMA_RETRY_MS = 60_000L;

    private final RestClient restClient;
    private final String collectionName;
    private final ObjectMapper mapper = new ObjectMapper();
    private volatile String collectionId;
    /** 大于当前时间表示 Chroma 不可用，值为下次重试时间戳；0 表示可用 */
    private volatile long chromaRetryAt = 0L;

    /** 内存降级存储 */
    private final Map<String, MemoryDoc> memoryStore = new ConcurrentHashMap<>();

    private static final class MemoryDoc {
        final String id;
        final float[] vector;
        final String document;
        final Map<String, Object> metadata;

        MemoryDoc(String id, float[] vector, String document, Map<String, Object> metadata) {
            this.id = id;
            this.vector = vector;
            this.document = document;
            this.metadata = metadata;
        }
    }

    public record Hit(String id, String document, Map<String, Object> metadata, double distance) {}

    public VectorStoreService(RestClient.Builder builder,
                              @Value("${app.ai.vector-store.base-url}") String baseUrl,
                              @Value("${app.ai.vector-store.collection:enterprise_kb}") String collectionName) {
        this.restClient = builder.baseUrl(baseUrl).build();
        this.collectionName = collectionName;
    }

    /** Chroma 是否处于可用状态（供启动重建索引判断） */
    public boolean isChromaHealthy() {
        return System.currentTimeMillis() >= chromaRetryAt;
    }

    public synchronized void upsert(List<String> ids, List<float[]> embeddings,
                                    List<String> documents, List<Map<String, Object>> metadatas) {
        if (isChromaHealthy()) {
            try {
                upsertChroma(ids, embeddings, documents, metadatas);
                ids.forEach(memoryStore::remove);
                return;
            } catch (Exception e) {
                chromaRetryAt = System.currentTimeMillis() + CHROMA_RETRY_MS;
                log.warn("向量库不可用，降级为内存检索，{} 秒后重试: {}",
                        CHROMA_RETRY_MS / 1000, e.getMessage());
            }
        }
        for (int i = 0; i < ids.size(); i++) {
            memoryStore.put(ids.get(i),
                    new MemoryDoc(ids.get(i), embeddings.get(i), documents.get(i), metadatas.get(i)));
        }
    }

    public void deleteByKnowledgeId(long knowledgeId) {
        memoryStore.values().removeIf(doc -> {
            Object v = doc.metadata.get("knowledge_id");
            return v instanceof Number n && n.longValue() == knowledgeId;
        });
        if (isChromaHealthy()) {
            try {
                deleteChroma(knowledgeId);
            } catch (Exception e) {
                chromaRetryAt = System.currentTimeMillis() + CHROMA_RETRY_MS;
                log.warn("向量库删除失败，降级处理: {}", e.getMessage());
            }
        }
    }

    public List<Hit> query(float[] embedding, int topK, String scheme) {
        if (isChromaHealthy()) {
            try {
                return queryChroma(embedding, topK, scheme);
            } catch (Exception e) {
                chromaRetryAt = System.currentTimeMillis() + CHROMA_RETRY_MS;
                log.warn("向量库查询失败，降级为内存检索: {}", e.getMessage());
            }
        }
        return queryMemory(embedding, topK, scheme);
    }

    private List<Hit> queryMemory(float[] embedding, int topK, String scheme) {
        List<Hit> scored = new ArrayList<>();
        for (MemoryDoc doc : memoryStore.values()) {
            if (scheme != null && !Objects.equals(doc.metadata.get("embed_scheme"), scheme)) {
                continue;
            }
            double similarity = cosine(embedding, doc.vector);
            scored.add(new Hit(doc.id, doc.document, doc.metadata, 1.0 - similarity));
        }
        scored.sort(Comparator.comparingDouble(Hit::distance));
        return scored.size() <= topK ? scored : new ArrayList<>(scored.subList(0, topK));
    }

    private static double cosine(float[] a, float[] b) {
        int len = Math.min(a.length, b.length);
        double dot = 0, na = 0, nb = 0;
        for (int i = 0; i < len; i++) {
            dot += (double) a[i] * b[i];
            na += (double) a[i] * a[i];
            nb += (double) b[i] * b[i];
        }
        if (na == 0 || nb == 0) {
            return 0;
        }
        return dot / (Math.sqrt(na) * Math.sqrt(nb));
    }

    private void upsertChroma(List<String> ids, List<float[]> embeddings,
                              List<String> documents, List<Map<String, Object>> metadatas) {
        ObjectNode body = mapper.createObjectNode();
        ArrayNode idArr = body.putArray("ids");
        ids.forEach(idArr::add);
        ArrayNode vecArr = body.putArray("embeddings");
        for (float[] vec : embeddings) {
            ArrayNode v = vecArr.addArray();
            for (float f : vec) {
                v.add(f);
            }
        }
        ArrayNode docArr = body.putArray("documents");
        documents.forEach(docArr::add);
        ArrayNode metaArr = body.putArray("metadatas");
        metadatas.forEach(meta -> metaArr.add(mapper.valueToTree(meta)));
        post("/api/v1/collections/" + ensureCollection() + "/upsert", body);
    }

    private void deleteChroma(long knowledgeId) {
        ObjectNode body = mapper.createObjectNode();
        body.putObject("where").putObject("knowledge_id").put("$eq", knowledgeId);
        post("/api/v1/collections/" + ensureCollection() + "/delete", body);
    }

    private List<Hit> queryChroma(float[] embedding, int topK, String scheme) {
        ObjectNode body = mapper.createObjectNode();
        ArrayNode queries = body.putArray("query_embeddings");
        ArrayNode v = queries.addArray();
        for (float f : embedding) {
            v.add(f);
        }
        body.put("n_results", topK);
        if (scheme != null) {
            body.putObject("where").putObject("embed_scheme").put("$eq", scheme);
        }
        JsonNode resp = post("/api/v1/collections/" + ensureCollection() + "/query", body);

        JsonNode ids = resp.path("ids").path(0);
        JsonNode docs = resp.path("documents").path(0);
        JsonNode metas = resp.path("metadatas").path(0);
        JsonNode dists = resp.path("distances").path(0);
        List<Hit> hits = new ArrayList<>();
        for (int i = 0; i < docs.size(); i++) {
            hits.add(new Hit(
                    ids.path(i).asText(""),
                    docs.path(i).asText(""),
                    mapper.convertValue(metas.path(i), Map.class),
                    dists.path(i).asDouble(1.0)));
        }
        return hits;
    }

    private String ensureCollection() {
        if (collectionId != null) {
            return collectionId;
        }
        synchronized (this) {
            if (collectionId != null) {
                return collectionId;
            }
            ObjectNode body = mapper.createObjectNode();
            body.put("name", collectionName);
            body.put("get_or_create", true);
            JsonNode resp = post("/api/v1/collections", body);
            String id = resp.path("id").asText(null);
            if (id == null) {
                throw new IllegalStateException("向量库集合创建失败");
            }
            collectionId = id;
            return collectionId;
        }
    }

    private JsonNode post(String uri, ObjectNode body) {
        try {
            String resp = restClient.post()
                    .uri(uri)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(String.class);
            return resp == null || resp.isBlank() ? mapper.createObjectNode() : mapper.readTree(resp);
        } catch (Exception e) {
            throw new IllegalStateException("向量库服务调用失败：" + e.getMessage(), e);
        }
    }
}
