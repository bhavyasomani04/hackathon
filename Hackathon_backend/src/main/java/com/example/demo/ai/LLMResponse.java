package com.example.demo.ai;

/**
 * Parsed LLM output. Matches the expected JSON shape from the brief:
 * <pre>{"agentId":"...","confidence":0.85,"reasoning":"..."}</pre>
 */
public record LLMResponse(String agentId, double confidence, String reasoning) {}
