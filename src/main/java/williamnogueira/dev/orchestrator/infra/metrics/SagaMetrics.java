package williamnogueira.dev.orchestrator.infra.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import williamnogueira.dev.orchestrator.domain.model.SagaEntity;

import java.time.Clock;
import java.time.Duration;
import java.util.Locale;

@Component
@RequiredArgsConstructor
public class SagaMetrics {

    private final MeterRegistry meterRegistry;
    private final Clock clock;

    public void sagaStarted(SagaEntity saga) {
        Counter.builder("saga.started")
                .tag("type", saga.getName())
                .register(meterRegistry)
                .increment();
    }

    public void sagaEnded(SagaEntity saga) {
        var outcome = saga.getStatus().name().toLowerCase(Locale.ROOT);

        Counter.builder("saga.ended")
                .tag("type", saga.getName())
                .tag("outcome", outcome)
                .register(meterRegistry)
                .increment();

        Timer.builder("saga.duration")
                .tag("type", saga.getName())
                .tag("outcome", outcome)
                .register(meterRegistry)
                .record(Duration.between(saga.getCreatedAt(), clock.instant()));
    }
}
