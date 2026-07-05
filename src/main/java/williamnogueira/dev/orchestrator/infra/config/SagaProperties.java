package williamnogueira.dev.orchestrator.infra.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "saga")
public record SagaProperties(Duration stepTimeout, int stepMaxAttempts) {
}
