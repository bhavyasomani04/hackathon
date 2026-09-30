package com.example.demo.exception;

import java.util.Set;

public class UnknownRoutingStrategyException extends RuntimeException {
    public UnknownRoutingStrategyException(String name, Set<String> known) {
        super("Unknown routing strategy '" + name + "'. Known strategies: " + known);
    }
}
