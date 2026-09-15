package com.enterprise.kb.controller;

import com.enterprise.kb.ai.QaService;
import com.enterprise.kb.util.Str;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * AI 问答接口：基于知识库向量检索回答用户提问。
 */
@RestController
@RequestMapping("/api/qa")
public class QaController {

    private final QaService qaService;

    public QaController(QaService qaService) {
        this.qaService = qaService;
    }

    @PostMapping("/ask")
    public Map<String, Object> ask(@RequestBody Map<String, Object> body) {
        return qaService.ask(Str.orEmpty(body.get("question")));
    }
}
