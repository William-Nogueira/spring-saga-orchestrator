package williamnogueira.dev.orchestrator.domain.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import williamnogueira.dev.orchestrator.domain.model.SagaEntity;
import williamnogueira.dev.orchestrator.domain.model.SagaRepository;
import williamnogueira.dev.orchestrator.domain.model.SagaStepEntity;
import williamnogueira.dev.orchestrator.domain.model.dto.StepCommand;
import williamnogueira.dev.orchestrator.domain.model.dto.StepReply;
import williamnogueira.dev.orchestrator.domain.model.enums.SagaStatus;
import williamnogueira.dev.orchestrator.domain.model.enums.StepStatus;
import williamnogueira.dev.orchestrator.infra.config.SagaProperties;
import williamnogueira.dev.orchestrator.infra.exception.SagaNotFoundException;
import williamnogueira.dev.orchestrator.infra.messaging.CommandDispatcher;
import williamnogueira.dev.orchestrator.infra.metrics.SagaMetrics;
import williamnogueira.dev.orchestrator.infra.utils.TransactionUtils;

import java.time.Clock;
import java.util.UUID;

import static java.util.Objects.isNull;

@Slf4j
@Service
@RequiredArgsConstructor
public class SagaOrchestrator {

    private final SagaRepository sagaRepository;
    private final CommandDispatcher commandDispatcher;
    private final StepTimeoutScheduler stepTimeoutScheduler;
    private final SagaProperties sagaProperties;
    private final SagaMetrics sagaMetrics;
    private final Clock clock;

    @Transactional
    public SagaEntity start(SagaEntity saga) {
        sagaRepository.save(saga);
        sagaMetrics.sagaStarted(saga);
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
        if (isNull(step)) {
            log.warn("ignoring reply for unknown step {} of saga {}", reply.stepId(), reply.sagaId());
            return;
        }

        switch (step.getStatus()) {
            case RUNNING -> onStepReply(saga, step, reply);
            case COMPENSATING -> onCompensationReply(saga, step, reply);
            default -> log.warn("ignoring duplicate or late reply for saga {} step {}", reply.sagaId(), reply.stepId());
        }
    }

    private void onStepReply(SagaEntity saga, SagaStepEntity step, StepReply reply) {
        if (reply.failure()) {
            step.transitionTo(StepStatus.FAILED);
            log.warn("saga {} failed at step '{}': {}; compensating", saga.getId(), step.getName(), reply.reason());
            beginCompensation(saga);
            return;
        }

        step.transitionTo(StepStatus.COMPLETED);
        dispatchNextStep(saga);
    }

    private void onCompensationReply(SagaEntity saga, SagaStepEntity step, StepReply reply) {
        if (reply.failure()) {
            step.transitionTo(StepStatus.COMPENSATION_FAILED);
            saga.transitionTo(SagaStatus.FAILED);
            sagaMetrics.sagaEnded(saga);
            log.error("compensation '{}' of saga {} failed: {}; manual intervention required",
                    step.getCompensation(), saga.getId(), reply.reason());
            return;
        }

        step.transitionTo(StepStatus.COMPENSATED);
        dispatchNextCompensation(saga);
    }

    private void beginCompensation(SagaEntity saga) {
        saga.transitionTo(SagaStatus.COMPENSATING);
        dispatchNextCompensation(saga);
    }

    private void dispatchNextCompensation(SagaEntity saga) {
        saga.lastCompletedStep().ifPresentOrElse(
                step -> {
                    step.markCompensating(clock.instant());
                    dispatchAfterCommit(
                            new StepCommand(saga.getId(), step.getId(), step.getCompensation(), saga.getPayload()),
                            step.getAttempts());
                },
                () -> {
                    saga.transitionTo(SagaStatus.COMPENSATED);
                    sagaMetrics.sagaEnded(saga);
                    log.info("saga {} compensated", saga.getId());
                });
    }

    private void dispatchNextStep(SagaEntity saga) {
        saga.nextPendingStep().ifPresentOrElse(
                step -> {
                    step.markDispatched(clock.instant());
                    dispatchAfterCommit(
                            new StepCommand(saga.getId(), step.getId(), step.getName(), saga.getPayload()),
                            step.getAttempts());
                },
                () -> {
                    saga.transitionTo(SagaStatus.COMPLETED);
                    sagaMetrics.sagaEnded(saga);
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
        if (isNull(step) || step.getAttempts() != attempt) {
            return;
        }

        if (step.getStatus() == StepStatus.RUNNING) {
            if (step.getAttempts() < sagaProperties.stepMaxAttempts()) {
                retryDispatch(saga, step, step.getName());
                return;
            }

            step.transitionTo(StepStatus.FAILED);
            log.warn("step '{}' of saga {} got no reply after {} attempts; compensating",
                    step.getName(), sagaId, step.getAttempts());
            beginCompensation(saga);
            return;
        }

        if (step.getStatus() == StepStatus.COMPENSATING) {
            if (step.getAttempts() < sagaProperties.stepMaxAttempts()) {
                retryDispatch(saga, step, step.getCompensation());
                return;
            }

            step.transitionTo(StepStatus.COMPENSATION_FAILED);
            saga.transitionTo(SagaStatus.FAILED);
            sagaMetrics.sagaEnded(saga);
            log.error("compensation '{}' of saga {} got no reply after {} attempts; manual intervention required",
                    step.getCompensation(), sagaId, step.getAttempts());
        }
    }

    @Transactional
    public SagaEntity retry(UUID sagaId) {
        var saga = sagaRepository.findWithStepsById(sagaId).orElseThrow(() -> new SagaNotFoundException(sagaId));
        if (saga.getStatus() != SagaStatus.FAILED) {
            throw new IllegalStateException("only FAILED sagas can be retried; saga %s is %s".formatted(sagaId, saga.getStatus()));
        }

        saga.transitionTo(SagaStatus.COMPENSATING);
        var failedCompensations = saga.getSteps().stream()
                .filter(step -> step.getStatus() == StepStatus.COMPENSATION_FAILED)
                .toList();

        if (failedCompensations.isEmpty()) {
            dispatchNextCompensation(saga);
        } else {
            failedCompensations.forEach(step -> {
                step.markCompensating(clock.instant());
                dispatchAfterCommit(
                        new StepCommand(saga.getId(), step.getId(), step.getCompensation(), saga.getPayload()),
                        step.getAttempts());
            });
        }

        log.info("saga {} manually retried; resuming compensation", sagaId);
        return saga;
    }

    @Transactional
    public SagaEntity forceCompensate(UUID sagaId) {
        var saga = sagaRepository.findWithStepsById(sagaId).orElseThrow(() -> new SagaNotFoundException(sagaId));
        if (saga.getStatus() != SagaStatus.STARTED) {
            throw new IllegalStateException("only STARTED sagas can be force-compensated; saga %s is %s".formatted(sagaId, saga.getStatus()));
        }

        saga.getSteps().stream()
                .filter(step -> step.getStatus() == StepStatus.RUNNING)
                .forEach(step -> step.transitionTo(StepStatus.FAILED));

        log.info("saga {} manually force-compensated", sagaId);
        beginCompensation(saga);
        return saga;
    }

    private void retryDispatch(SagaEntity saga, SagaStepEntity step, String command) {
        step.redispatch(clock.instant());
        log.warn("no reply for '{}' of saga {}; retrying (attempt {}/{})",
                command, saga.getId(), step.getAttempts(), sagaProperties.stepMaxAttempts());
        dispatchAfterCommit(
                new StepCommand(saga.getId(), step.getId(), command, saga.getPayload()),
                step.getAttempts());
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
