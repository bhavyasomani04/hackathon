package com.example.demo.exception;

public class NoAvailableAgentsException extends RuntimeException {
    public NoAvailableAgentsException(String orderId) {
        super("No available agents to recommend for order '" + orderId + "'");
    }
}
