package williamnogueira.dev.orchestrator.domain.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import williamnogueira.dev.orchestrator.domain.model.SagaEntity;
import williamnogueira.dev.orchestrator.domain.model.SagaRepository;
import williamnogueira.dev.orchestrator.domain.model.dto.StepCommand;
import williamnogueira.dev.orchestrator.domain.model.dto.StepReply;
import williamnogueira.dev.orchestrator.domain.model.enums.SagaStatus;
import williamnogueira.dev.orchestrator.domain.model.enums.StepStatus;
import williamnogueira.dev.orchestrator.infra.messaging.CommandDispatcher;
import williamnogueira.dev.orchestrator.infra.utils.TransactionUtils;

import java.time.Instant;
import java.util.UUID;

import static java.util.Objects.isNull;

@Service
@RequiredArgsConstructor
@Slf4j
public class SagaOrchestrator {

    private final SagaRepository sagaRepository;
    private final CommandDispatcher commandDispatcher;
    private final StepTimeoutScheduler stepTimeoutScheduler;

    @Transactional
    public SagaEntity start(SagaEntity saga) {
        sagaRepository.save(saga);
        dispatchNextStep(saga);

        log.info("saga {} started", saga.getId());
        return saga;
    }

    @Transactional
    public void onReply(StepReply reply) {
        var saga = sagaRepository.findWithStepsById(reply.sagaId()).orElse(null);
        if (isNull(saga)) {
            log.warn("ignoring reply for unknown saga {}", reply.sagaId());
            return;
        }

        var step = saga.findStep(reply.stepId()).orElse(null);
        if (isNull(step) || step.getStatus() != StepStatus.RUNNING) {
            log.warn("ignoring duplicate or late reply for saga {} step {}", reply.sagaId(), reply.stepId());
            return;
        }

        if (reply.failure()) {
            step.transitionTo(StepStatus.FAILED);
            saga.transitionTo(SagaStatus.FAILED);
            log.warn("saga {} failed at step '{}': {}", saga.getId(), step.getName(), reply.reason());
            return;
        }

        step.transitionTo(StepStatus.COMPLETED);
        dispatchNextStep(saga);
    }

    private void dispatchNextStep(SagaEntity saga) {
        saga.nextPendingStep().ifPresentOrElse(
                step -> {
                    step.markDispatched(Instant.now());
                    dispatchAfterCommit(
                            new StepCommand(saga.getId(), step.getId(), step.getName(), saga.getPayload()),
                            step.getAttempts());
                },
                () -> {
                    saga.transitionTo(SagaStatus.COMPLETED);
                    log.info("saga {} completed", saga.getId());
                });
    }

    @Transactional
    public void onStepTimeout(UUID sagaId, UUID stepId, int attempt) {
        var saga = sagaRepository.findWithStepsById(sagaId).orElse(null);
        if (isNull(saga)) {
            return;
        }

        var step = saga.findStep(stepId).orElse(null);
        if (isNull(step) || step.getStatus() != StepStatus.RUNNING || step.getAttempts() != attempt) {
            return;
        }

        step.transitionTo(StepStatus.FAILED);
        saga.transitionTo(SagaStatus.FAILED);
        log.warn("step '{}' of saga {} got no reply in time; saga failed", step.getName(), sagaId);
    }

    private void dispatchAfterCommit(StepCommand command, int attempt) {
        TransactionUtils.executeAfterCommit(
                () -> commandDispatcher.dispatch(command),
                () -> log.error("dispatch of step '{}' for saga {} failed; the armed timeout will fail the step",
                        command.stepName(), command.sagaId()));

        TransactionUtils.executeAfterCommit(
                () -> stepTimeoutScheduler.scheduleTimeoutCheck(command.sagaId(), command.stepId(), attempt));
    }
}
