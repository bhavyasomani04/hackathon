package com.example.demo.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * Enables {@code @Async} for the T-4 agentic loop.
 *
 * <p>Bounded pool so a burst of OFFLINE events (or an accidental loop) cannot
 * spawn unbounded threads. The re-plan is I/O-bound (LLM + DB), so a small
 * pool with a modest queue is plenty.
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean(name = "replanExecutor")
    public Executor replanExecutor() {
        ThreadPoolTaskExecutor ex = new ThreadPoolTaskExecutor();
        ex.setCorePoolSize(2);
        ex.setMaxPoolSize(4);
        ex.setQueueCapacity(50);
        ex.setThreadNamePrefix("replan-");
        ex.initialize();
        return ex;
    }
}
