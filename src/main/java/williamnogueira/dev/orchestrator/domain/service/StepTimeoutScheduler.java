package williamnogueira.dev.orchestrator.domain.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;
import williamnogueira.dev.orchestrator.infra.config.SagaProperties;

import java.time.Clock;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class StepTimeoutScheduler {

    private final TaskScheduler taskScheduler;
    private final ObjectProvider<SagaOrchestrator> sagaOrchestrator;
    private final SagaProperties sagaProperties;
    private final Clock clock;

    public void scheduleTimeoutCheck(UUID sagaId, UUID stepId, int attempt) {
        var checkAt = clock.instant().plus(sagaProperties.stepTimeout().multipliedBy(attempt));

        taskScheduler.schedule(() -> {
            MDC.put("sagaId", String.valueOf(sagaId));
            try {
                sagaOrchestrator.getObject().onStepTimeout(sagaId, stepId, attempt);
            } finally {
                MDC.remove("sagaId");
            }
        }, checkAt);

        log.debug("timeout check for step {} of saga {} (attempt {}) armed for {}", stepId, sagaId, attempt, checkAt);
    }
}
