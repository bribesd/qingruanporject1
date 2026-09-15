package com.enterprise.kb.ai;

import com.enterprise.kb.exception.ApiException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * AI 问答：向量召回相关片段 ->（可选）调用 OpenAI 兼容 LLM 生成回答 -> 附带引用来源。
 * 未配置 LLM 时退化为检索式回答（直接返回最相关片段）。
 */
@Service
public class QaService {

    private final SearchService searchService;
    private final RestClient llmClient;
    private final String llmModel;
    private final ObjectMapper mapper = new ObjectMapper();

    public QaService(SearchService searchService,
                     RestClient.Builder builder,
                     @Value("${app.ai.qa.llm-base-url:}") String llmBaseUrl,
                     @Value("${app.ai.qa.llm-model:}") String llmModel,
                     @Value("${app.ai.qa.llm-api-key:}") String llmApiKey) {
        this.searchService = searchService;
        this.llmModel = llmModel;
        this.llmClient = (llmBaseUrl == null || llmBaseUrl.isBlank()) ? null
                : builder.baseUrl(llmBaseUrl)
                        .defaultHeader("Authorization", "Bearer " + (llmApiKey == null ? "" : llmApiKey))
                        .build();
    }

    public Map<String, Object> ask(String question) {
        if (question == null || question.isBlank()) {
            throw new ApiException(400, "问题不能为空");
        }
        List<SearchService.SearchResult> sources = searchService.search(question.trim());
        if (sources.isEmpty()) {
            Map<String, Object> empty = new LinkedHashMap<>();
            empty.put("answer", "知识库中未找到与该问题相关的内容，请尝试更换问法或先上传相关文档。");
            empty.put("sources", List.of());
            return empty;
        }
        String context = buildContext(sources);
        String answer = llmClient != null ? generate(question.trim(), context, sources) : extractAnswer(sources);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("answer", answer);
        result.put("sources", sources);
        return result;
    }

    private String buildContext(List<SearchService.SearchResult> sources) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < sources.size(); i++) {
            sb.append("[").append(i + 1).append("] ").append(sources.get(i).title())
                    .append('\n').append(sources.get(i).snippet()).append("\n\n");
        }
        return sb.toString();
    }

    private String extractAnswer(List<SearchService.SearchResult> sources) {
        StringBuilder sb = new StringBuilder("根据知识库检索结果：\n\n");
        for (int i = 0; i < Math.min(3, sources.size()); i++) {
            SearchService.SearchResult s = sources.get(i);
            sb.append("【").append(s.title()).append("】").append(abbreviate(s.snippet(), 200)).append("\n\n");
        }
        return sb.toString();
    }

    private String generate(String question, String context, List<SearchService.SearchResult> sources) {
        try {
            Map<String, Object> body = Map.of(
                    "model", llmModel,
                    "temperature", 0.2,
                    "messages", List.of(
                            Map.of("role", "system", "content",
                                    "你是企业知识库助手，仅依据提供的资料回答问题，并标注引用编号；资料中没有的内容请如实说明。"),
                            Map.of("role", "user", "content", "资料：\n" + context + "\n问题：" + question)));
            String resp = llmClient.post()
                    .uri("/v1/chat/completions")
                    .body(body)
                    .retrieve()
                    .body(String.class);
            String content = mapper.readTree(resp)
                    .path("choices").path(0).path("message").path("content").asText("");
            return content.isBlank() ? extractAnswer(sources) : content;
        } catch (Exception e) {
            return extractAnswer(sources);
        }
    }

    private String abbreviate(String s, int max) {
        String text = s == null ? "" : s.trim();
        return text.length() <= max ? text : text.substring(0, max) + "…";
    }
}
