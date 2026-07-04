package williamnogueira.dev.orchestrator.domain.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Component
@Slf4j
public class StepTimeoutScheduler {

    private final TaskScheduler taskScheduler;
    private final ObjectProvider<SagaOrchestrator> sagaOrchestrator;
    private final Duration stepTimeout;

    public StepTimeoutScheduler(TaskScheduler taskScheduler,
                                ObjectProvider<SagaOrchestrator> sagaOrchestrator,
                                @Value("${saga.step-timeout}") Duration stepTimeout) {
        this.taskScheduler = taskScheduler;
        this.sagaOrchestrator = sagaOrchestrator;
        this.stepTimeout = stepTimeout;
    }

    public void scheduleTimeoutCheck(UUID sagaId, UUID stepId, int attempt) {
        taskScheduler.schedule(
                () -> sagaOrchestrator.getObject().onStepTimeout(sagaId, stepId, attempt),
                Instant.now().plus(stepTimeout));
    }
}
