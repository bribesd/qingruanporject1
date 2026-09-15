package com.enterprise.kb.ai;

import com.enterprise.kb.exception.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 模块4：AI 问答与搜索核心。负责建立向量索引与向量相似度检索。
 * 索引失败仅记录警告日志，不阻断知识创建/更新主流程。
 */
@Service
public class SearchService {

    private static final Logger log = LoggerFactory.getLogger(SearchService.class);

    private final JdbcTemplate jdbcTemplate;
    private final EmbeddingService embeddingService;
    private final VectorStoreService vectorStoreService;
    private final int topK;

    public SearchService(JdbcTemplate jdbcTemplate,
                         EmbeddingService embeddingService,
                         VectorStoreService vectorStoreService,
                         @Value("${app.ai.qa.top-k:5}") int topK) {
        this.jdbcTemplate = jdbcTemplate;
        this.embeddingService = embeddingService;
        this.vectorStoreService = vectorStoreService;
        this.topK = topK;
    }

    public record SearchResult(long knowledgeId, String title, String category, String snippet, double score) {}

    /** 建立/刷新某条知识的向量索引（先清理旧索引，再切分、向量化、入库） */
    public void indexKnowledge(long knowledgeId, String title, Long categoryId, String content) {
        try {
            removeKnowledge(knowledgeId);
            List<String> chunks = embeddingService.chunk(content);
            if (chunks.isEmpty()) {
                return;
            }
            List<float[]> vectors = embeddingService.embed(chunks);
            List<String> ids = new ArrayList<>();
            List<Map<String, Object>> metas = new ArrayList<>();
            for (int i = 0; i < chunks.size(); i++) {
                ids.add(knowledgeId + ":" + i);
                metas.add(Map.of(
                        "knowledge_id", knowledgeId,
                        "title", title == null ? "" : title,
                        "category_id", categoryId == null ? 0 : categoryId,
                        "embed_scheme", embeddingService.currentScheme()));
                jdbcTemplate.update(
                        "INSERT INTO knowledge_chunks (knowledge_id, chunk_index, content) VALUES (?, ?, ?)",
                        knowledgeId, i, chunks.get(i));
            }
            vectorStoreService.upsert(ids, vectors, chunks, metas);
        } catch (Exception e) {
            log.warn("知识 {} 向量索引失败: {}", knowledgeId, e.getMessage());
        }
    }

    /** 删除知识时同步清理向量库向量与数据库文本片段 */
    public void removeKnowledge(long knowledgeId) {
        try {
            vectorStoreService.deleteByKnowledgeId(knowledgeId);
        } catch (Exception e) {
            log.warn("知识 {} 向量删除失败: {}", knowledgeId, e.getMessage());
        }
        jdbcTemplate.update("DELETE FROM knowledge_chunks WHERE knowledge_id = ?", knowledgeId);
    }

    /** 以默认 topK 检索（问答模块使用） */
    public List<SearchResult> search(String query) {
        return search(query, topK);
    }

    public List<SearchResult> search(String query, int limit) {
        if (query == null || query.isBlank()) {
            throw new ApiException(400, "搜索关键词不能为空");
        }
        float[] queryVector = embeddingService.embedOne(query.trim());
        List<VectorStoreService.Hit> hits = vectorStoreService.query(
                queryVector, Math.min(Math.max(limit, 1), topK * 4), embeddingService.currentScheme());

        Map<Long, SearchResult> best = new LinkedHashMap<>();
        for (VectorStoreService.Hit hit : hits) {
            Object kidValue = hit.metadata().get("knowledge_id");
            long kid = kidValue instanceof Number n ? n.longValue() : 0;
            if (kid <= 0 || best.containsKey(kid)) {
                continue;
            }
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    "SELECT k.title, c.name AS category FROM knowledge_items k " +
                            "LEFT JOIN categories c ON k.category_id = c.id WHERE k.id = ?", kid);
            if (rows.isEmpty()) {
                continue;
            }
            best.put(kid, new SearchResult(kid,
                    String.valueOf(rows.get(0).getOrDefault("title", "")),
                    String.valueOf(rows.get(0).getOrDefault("category", "")),
                    hit.document(),
                    Math.max(0.0, 1.0 - hit.distance())));
        }
        return new ArrayList<>(best.values());
    }
}
