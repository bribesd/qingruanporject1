package com.enterprise.kb.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 模块2：文本向量化。优先调用外部 Sentence-BERT HTTP 服务：
 * POST /embed {"model": "...", "texts": [...]} -> {"embeddings": [[...], ...]}
 * 服务不可用时自动降级为内置字符 n-gram 哈希向量（确定性、无外部依赖），
 * 每隔 REMOTE_RETRY_MS 重试远程服务，恢复后自动切回。
 */
@Service
public class EmbeddingService {

    private static final Logger log = LoggerFactory.getLogger(EmbeddingService.class);

    private static final int CHUNK_SIZE = 500;
    private static final int CHUNK_OVERLAP = 80;
    /** 降级向量维度 */
    private static final int LOCAL_DIM = 256;
    /** 远程服务失败后的重试间隔（毫秒） */
    private static final long REMOTE_RETRY_MS = 60_000L;

    private final RestClient restClient;
    private final String model;
    private final ObjectMapper mapper = new ObjectMapper();
    /** 大于当前时间表示远程不可用，值为下次重试时间戳；0 表示远程可用 */
    private volatile long remoteRetryAt = 0L;

    public EmbeddingService(RestClient.Builder builder,
                            @Value("${app.ai.embedding.base-url}") String baseUrl,
                            @Value("${app.ai.embedding.model:sentence-bert}") String model) {
        this.restClient = builder.baseUrl(baseUrl).build();
        this.model = model;
    }

    public List<float[]> embed(List<String> texts) {
        if (texts.isEmpty()) {
            return List.of();
        }
        if (System.currentTimeMillis() >= remoteRetryAt) {
            try {
                List<float[]> result = embedRemote(texts);
                remoteRetryAt = 0L;
                return result;
            } catch (Exception e) {
                remoteRetryAt = System.currentTimeMillis() + REMOTE_RETRY_MS;
                log.warn("向量化服务不可用，降级为本地 n-gram 向量，{} 秒后重试: {}",
                        REMOTE_RETRY_MS / 1000, e.getMessage());
            }
        }
        return texts.stream().map(EmbeddingService::localEmbed).collect(Collectors.toList());
    }

    public float[] embedOne(String text) {
        return embed(List.of(text)).get(0);
    }

    /** 当前是否处于本地降级模式 */
    public boolean isFallbackActive() {
        return System.currentTimeMillis() < remoteRetryAt;
    }

    /** 当前向量化方案标识，写入向量 metadata 用于检索过滤，避免混合向量空间 */
    public String currentScheme() {
        return isFallbackActive() ? "local" : "remote";
    }

    private List<float[]> embedRemote(List<String> texts) throws Exception {
        String body = restClient.post()
                .uri("/embed")
                .body(Map.of("model", model, "texts", texts))
                .retrieve()
                .body(String.class);
        JsonNode root = mapper.readTree(body);
        JsonNode embeddings = root.has("embeddings") ? root.get("embeddings") : root.path("data");
        if (!embeddings.isArray() || embeddings.size() != texts.size()) {
            throw new IllegalStateException("向量化服务返回格式异常");
        }
        List<float[]> result = new ArrayList<>(texts.size());
        for (JsonNode arr : embeddings) {
            float[] vec = new float[arr.size()];
            for (int i = 0; i < arr.size(); i++) {
                vec[i] = (float) arr.get(i).asDouble();
            }
            result.add(vec);
        }
        return result;
    }

    /** 长文本切分：固定长度 + 重叠，优先在句号等边界断开 */
    public List<String> chunk(String text) {
        List<String> chunks = new ArrayList<>();
        String normalized = text.replaceAll("\\s+", " ").trim();
        if (normalized.length() <= CHUNK_SIZE) {
            chunks.add(normalized);
            return chunks;
        }
        int start = 0;
        while (start < normalized.length()) {
            int end = Math.min(start + CHUNK_SIZE, normalized.length());
            if (end < normalized.length()) {
                int boundary = lastSentenceBoundary(normalized, start + CHUNK_SIZE / 2, end);
                if (boundary > 0) {
                    end = boundary;
                }
            }
            String piece = normalized.substring(start, end).trim();
            if (!piece.isEmpty()) {
                chunks.add(piece);
            }
            if (end >= normalized.length()) {
                break;
            }
            start = Math.max(end - CHUNK_OVERLAP, start + 1);
        }
        return chunks;
    }

    private int lastSentenceBoundary(String text, int from, int to) {
        for (int i = Math.min(to, text.length()) - 1; i > from; i--) {
            char c = text.charAt(i);
            if (c == '。' || c == '！' || c == '？' || c == '.' || c == '!' || c == '?') {
                return i + 1;
            }
        }
        return -1;
    }

    /** 内置降级向量化：字符 unigram + bigram 哈希计数，L2 归一化（确定性） */
    static float[] localEmbed(String text) {
        float[] vec = new float[LOCAL_DIM];
        String s = text == null ? "" : text.toLowerCase();
        for (int i = 0; i < s.length(); i++) {
            bump(vec, s.substring(i, i + 1));
            if (i + 1 < s.length()) {
                bump(vec, s.substring(i, i + 2));
            }
        }
        float norm = 0f;
        for (float v : vec) {
            norm += v * v;
        }
        norm = (float) Math.sqrt(norm);
        if (norm > 0f) {
            for (int i = 0; i < LOCAL_DIM; i++) {
                vec[i] /= norm;
            }
        }
        return vec;
    }

    private static void bump(float[] vec, String gram) {
        int idx = Math.floorMod(gram.hashCode(), LOCAL_DIM);
        vec[idx] += 1f;
    }
}
