package com.example.demo.ai;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses raw LLM text into an {@link LLMResponse}.
 *
 * <p>LLMs frequently wrap JSON in markdown fences (```json ... ```) or
 * add prose around the object. We strip fences and extract the first
 * balanced {@code {...}} block before parsing.
 */
@Component
public class LLMResponseParser {

    private static final Pattern FENCE = Pattern.compile("```(?:json)?\\s*([\\s\\S]*?)\\s*```",
            Pattern.CASE_INSENSITIVE);

    private final ObjectMapper mapper;

    public LLMResponseParser(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    /** @throws RuntimeException on any parse issue — caller handles fallback. */
    public LLMResponse parse(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new RuntimeException("LLM returned empty response");
        }

        String candidate = raw.trim();
        Matcher m = FENCE.matcher(candidate);
        if (m.find()) {
            candidate = m.group(1).trim();
        }

        int start = candidate.indexOf('{');
        int end   = candidate.lastIndexOf('}');
        if (start < 0 || end <= start) {
            throw new RuntimeException("No JSON object found in LLM response: " + truncate(raw));
        }
        String json = candidate.substring(start, end + 1);

        try {
            JsonNode node = mapper.readTree(json);
            String agentId = text(node, "agentId");
            if (agentId == null || agentId.isBlank()) {
                throw new RuntimeException("Missing 'agentId' in LLM response");
            }
            double confidence = node.hasNonNull("confidence") ? node.get("confidence").asDouble() : 0.5;
            String reasoning  = text(node, "reasoning");
            if (reasoning == null) reasoning = "(no reasoning supplied)";
            return new LLMResponse(agentId.trim(), clamp01(confidence), reasoning.trim());
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("LLM JSON parse failed: " + truncate(json), e);
        }
    }

    private static String text(JsonNode node, String field) {
        return node.hasNonNull(field) ? node.get(field).asText() : null;
    }

    private static double clamp01(double v) {
        if (Double.isNaN(v)) return 0.0;
        return Math.max(0.0, Math.min(1.0, v));
    }

    private static String truncate(String s) {
        return s.length() > 200 ? s.substring(0, 200) + "…" : s;
    }
}
