package williamnogueira.dev.orchestrator.domain.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import williamnogueira.dev.orchestrator.domain.model.SagaStepRepository;
import williamnogueira.dev.orchestrator.domain.model.enums.StepStatus;
import williamnogueira.dev.orchestrator.infra.config.SagaProperties;

import java.time.Clock;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class SagaRecoverySweeper {

    private final SagaStepRepository sagaStepRepository;
    private final SagaOrchestrator sagaOrchestrator;
    private final SagaProperties sagaProperties;
    private final Clock clock;

    @EventListener(ApplicationReadyEvent.class)
    public void recoverOnStartup() {
        log.info("recovering in-flight sagas after startup");
        sweep();
    }

    @Scheduled(fixedDelayString = "${saga.sweep-interval}")
    public void sweep() {
        var now = clock.instant();
        var inFlight = sagaStepRepository.findByStatusIn(List.of(StepStatus.RUNNING, StepStatus.COMPENSATING));

        for (var step : inFlight) {
            var deadline = step.getDispatchedAt().plus(sagaProperties.stepTimeout().multipliedBy(step.getAttempts()));
            if (now.isAfter(deadline)) {
                log.warn("sweeper found stuck step '{}' of saga {} (attempt {}, dispatched {})",
                        step.getName(), step.getSaga().getId(), step.getAttempts(), step.getDispatchedAt());
                sagaOrchestrator.onStepTimeout(step.getSaga().getId(), step.getId(), step.getAttempts());
            }
        }
    }
}
