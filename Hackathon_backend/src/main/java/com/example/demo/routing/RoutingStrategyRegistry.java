package com.example.demo.routing;

import com.example.demo.exception.UnknownRoutingStrategyException;
import jakarta.annotation.PostConstruct;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Resolves the currently active {@link RoutingStrategy} from config.
 *
 * <p>Runtime switchability: {@link #active()} reads {@code routing.strategy}
 * from {@link Environment} on every call — not cached — so changing the
 * property (env var, refresh scope, config server) takes effect on the next
 * suggestion without a restart.
 *
 * <p>Same registry is used from both call sites (HTTP endpoint and the T-4
 * async event listener), so both always see the same active strategy.
 */
@Component
public class RoutingStrategyRegistry {

    private static final String CONFIG_KEY = "routing.strategy";
    private static final String DEFAULT_STRATEGY = RuleBasedStrategy.NAME;

    private final Map<String, RoutingStrategy> byName;
    private final Environment environment;

    public RoutingStrategyRegistry(List<RoutingStrategy> strategies, Environment environment) {
        this.byName = strategies.stream()
                .collect(Collectors.toMap(RoutingStrategy::name, s -> s));
        this.environment = environment;
    }

    @PostConstruct
    void validateOnStartup() {
        String configured = environment.getProperty(CONFIG_KEY, DEFAULT_STRATEGY);
        if (!byName.containsKey(configured)) {
            throw new IllegalStateException(
                    "Configured " + CONFIG_KEY + "='" + configured
                            + "' does not match any registered strategy. Known: " + byName.keySet());
        }
    }

    public RoutingStrategy active() {
        String name = environment.getProperty(CONFIG_KEY, DEFAULT_STRATEGY);
        RoutingStrategy strategy = byName.get(name);
        if (strategy == null) {
            throw new UnknownRoutingStrategyException(name, byName.keySet());
        }
        return strategy;
    }
}
