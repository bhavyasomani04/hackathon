package com.example.demo.exception;

public class DuplicateOrderException extends RuntimeException {
    public DuplicateOrderException(String orderId) {
        super("Order with id '" + orderId + "' already exists");
    }
}
