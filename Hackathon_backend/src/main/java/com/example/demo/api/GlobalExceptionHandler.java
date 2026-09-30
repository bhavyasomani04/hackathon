package com.example.demo.api;

import com.example.demo.exception.AgentNotFoundException;
import com.example.demo.exception.DuplicateOrderException;
import com.example.demo.exception.IllegalAgentStateException;
import com.example.demo.exception.IllegalSuggestionStateException;
import com.example.demo.exception.NoAvailableAgentsException;
import com.example.demo.exception.OrderNotFoundException;
import com.example.demo.exception.SuggestionNotFoundException;
import com.example.demo.exception.UnknownRoutingStrategyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(DuplicateOrderException.class)
    public ResponseEntity<Map<String, Object>> handleDuplicate(DuplicateOrderException ex) {
        return build(HttpStatus.CONFLICT, "duplicate_order", ex.getMessage());
    }

    @ExceptionHandler(AgentNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleAgentNotFound(AgentNotFoundException ex) {
        return build(HttpStatus.NOT_FOUND, "agent_not_found", ex.getMessage());
    }

    @ExceptionHandler(IllegalAgentStateException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalAgentState(IllegalAgentStateException ex) {
        return build(HttpStatus.CONFLICT, "illegal_agent_state", ex.getMessage());
    }

    @ExceptionHandler(SuggestionNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleSuggestionNotFound(SuggestionNotFoundException ex) {
        return build(HttpStatus.NOT_FOUND, "suggestion_not_found", ex.getMessage());
    }

    @ExceptionHandler(IllegalSuggestionStateException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalSuggestionState(IllegalSuggestionStateException ex) {
        return build(HttpStatus.CONFLICT, "illegal_suggestion_state", ex.getMessage());
    }

    @ExceptionHandler(OrderNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleOrderNotFound(OrderNotFoundException ex) {
        return build(HttpStatus.NOT_FOUND, "order_not_found", ex.getMessage());
    }

    @ExceptionHandler(NoAvailableAgentsException.class)
    public ResponseEntity<Map<String, Object>> handleNoAvailableAgents(NoAvailableAgentsException ex) {
        return build(HttpStatus.CONFLICT, "no_available_agents", ex.getMessage());
    }

    @ExceptionHandler(UnknownRoutingStrategyException.class)
    public ResponseEntity<Map<String, Object>> handleUnknownStrategy(UnknownRoutingStrategyException ex) {
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "unknown_routing_strategy", ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> fields = ex.getBindingResult().getFieldErrors().stream()
                .collect(Collectors.toMap(
                        fe -> fe.getField(),
                        fe -> fe.getDefaultMessage() == null ? "invalid" : fe.getDefaultMessage(),
                        (a, b) -> a));
        Map<String, Object> body = baseBody("validation_failed", "Request body validation failed");
        body.put("fields", fields);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Map<String, Object>> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        String msg = "Parameter '" + ex.getName() + "' has invalid value '" + ex.getValue() + "'";
        return build(HttpStatus.BAD_REQUEST, "invalid_parameter", msg);
    }

    private ResponseEntity<Map<String, Object>> build(HttpStatus status, String error, String message) {
        return ResponseEntity.status(status).body(baseBody(error, message));
    }

    private Map<String, Object> baseBody(String error, String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", error);
        body.put("message", message);
        body.put("timestamp", LocalDateTime.now().toString());
        return body;
    }
}
