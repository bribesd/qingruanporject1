package com.enterprise.kb.controller;

import com.enterprise.kb.ai.SearchService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 语义搜索接口：基于向量相似度返回最相关知识条目与片段。
 */
@RestController
@RequestMapping("/api/search")
public class SearchController {

    private final SearchService searchService;

    public SearchController(SearchService searchService) {
        this.searchService = searchService;
    }

    @GetMapping
    public List<SearchService.SearchResult> search(
            @RequestParam("q") String q,
            @RequestParam(required = false) Integer limit) {
        return searchService.search(q, limit == null ? 10 : limit);
    }
}
