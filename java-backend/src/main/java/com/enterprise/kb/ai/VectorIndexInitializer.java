package com.enterprise.kb.ai;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 启动时重建向量索引：
 * 1) 补偿内存降级存储的重启丢失；
 * 2) 恢复 embed_scheme 一致性（降级/远程切换后重新对齐全量向量）。
 * 可通过 app.ai.reindex-on-startup=false 关闭。
 */
@Component
public class VectorIndexInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(VectorIndexInitializer.class);

    private final JdbcTemplate jdbcTemplate;
    private final SearchService searchService;
    private final boolean enabled;

    public VectorIndexInitializer(JdbcTemplate jdbcTemplate,
                                  SearchService searchService,
                                  @Value("${app.ai.reindex-on-startup:true}") boolean enabled) {
        this.jdbcTemplate = jdbcTemplate;
        this.searchService = searchService;
        this.enabled = enabled;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!enabled) {
            return;
        }
        List<Map<String, Object>> items = jdbcTemplate.queryForList(
                "SELECT id, title, category_id, content FROM knowledge_items");
        int indexed = 0;
        for (Map<String, Object> item : items) {
            long id = ((Number) item.get("id")).longValue();
            Object categoryObj = item.get("category_id");
            Long categoryId = categoryObj instanceof Number n ? n.longValue() : null;
            try {
                searchService.indexKnowledge(id,
                        String.valueOf(item.getOrDefault("title", "")),
                        categoryId,
                        String.valueOf(item.getOrDefault("content", "")));
                indexed++;
            } catch (Exception e) {
                log.warn("启动重建索引失败，知识 {}: {}", id, e.getMessage());
            }
        }
        log.info("启动向量索引重建完成，共处理 {} 条知识", indexed);
    }
}
