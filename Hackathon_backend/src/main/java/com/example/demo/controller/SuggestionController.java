package com.example.demo.controller;

import com.example.demo.api.dto.ResolveSuggestionRequest;
import com.example.demo.api.dto.SuggestionResponse;
import com.example.demo.entity.SuggestionStatus;
import com.example.demo.service.SuggestionService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/suggestions")
public class SuggestionController {

    private final SuggestionService suggestionService;

    public SuggestionController(SuggestionService suggestionService) {
        this.suggestionService = suggestionService;
    }

    @GetMapping
    public List<SuggestionResponse> list(@RequestParam(required = false) SuggestionStatus status) {
        return suggestionService.list(status);
    }

    @PatchMapping("/{id}")
    public SuggestionResponse resolve(@PathVariable Long id,
                                      @Valid @RequestBody ResolveSuggestionRequest request) {
        return suggestionService.resolve(id, request.getStatus());
    }
}
