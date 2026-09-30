package com.example.demo.ai;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

/**
 * Adapted from the brief's Addendum B.
 *
 * <p>Handles the provider-specific HTTP wire format for Gemini Flash,
 * Groq, and Ollama. Returns the raw text content of the model's response.
 *
 * <p>What this handles: authentication headers, request body shape,
 * response unwrapping.
 *
 * <p>What lives elsewhere: prompt construction ({@link PromptBuilder}),
 * JSON parsing ({@link LLMResponseParser}), agent-id validation and
 * fallback logic (in the AI strategy).
 *
 * <p>Configure in {@code application.properties}:
 * <pre>
 *   llm.provider = gemini | groq | ollama
 *   llm.api-key  = ${LLM_API_KEY}   (not needed for ollama)
 *   llm.model    = gemini-1.5-flash
 *   llm.base-url = https://generativelanguage.googleapis.com
 * </pre>
 */
@Component
public class LLMGateway {

    @Value("${llm.provider}")
    private String provider;

    @Value("${llm.api-key:}")
    private String apiKey;

    @Value("${llm.model}")
    private String model;

    @Value("${llm.base-url}")
    private String baseUrl;

    private final RestClient http = RestClient.create();

    /**
     * Sends prompt to the configured LLM and returns the raw text response.
     * Throws RuntimeException on HTTP error or unparseable envelope —
     * caller handles fallback.
     */
    public String callLLM(String prompt) {
        return switch (provider.toLowerCase()) {
            case "gemini" -> callGemini(prompt);
            case "groq"   -> callOpenAICompatible(prompt, baseUrl + "/openai/v1/chat/completions");
            case "ollama" -> callOpenAICompatible(prompt, baseUrl + "/v1/chat/completions");
            default       -> throw new IllegalStateException("Unknown LLM provider: " + provider);
        };
    }

    private String callGemini(String prompt) {
        var url  = baseUrl + "/v1beta/models/" + model + ":generateContent?key=" + apiKey;
        var body = Map.of("contents", List.of(
                Map.of("parts", List.of(Map.of("text", prompt)))));

        Map<?, ?> resp = http.post().uri(url)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body).retrieve().body(Map.class);

        try {
            var candidates = (List<?>) resp.get("candidates");
            var content    = (Map<?, ?>) ((Map<?, ?>) candidates.get(0)).get("content");
            var parts      = (List<?>) content.get("parts");
            return (String) ((Map<?, ?>) parts.get(0)).get("text");
        } catch (Exception e) {
            throw new RuntimeException("Gemini response parse failed", e);
        }
    }

    private String callOpenAICompatible(String prompt, String url) {
        var body = Map.of(
                "model", model,
                "messages", List.of(Map.of(
                        "role", "user",
                        "content", prompt)));

        Map<?, ?> resp = http.post().uri(url)
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + apiKey)
                .body(body).retrieve().body(Map.class);

        try {
            var choices = (List<?>) resp.get("choices");
            var message = (Map<?, ?>) ((Map<?, ?>) choices.get(0)).get("message");
            return (String) message.get("content");
        } catch (Exception e) {
            throw new RuntimeException("LLM response parse failed", e);
        }
    }
}
