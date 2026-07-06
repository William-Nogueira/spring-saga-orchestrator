package williamnogueira.dev.orchestrator.domain.service;

import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import williamnogueira.dev.orchestrator.TestcontainersConfiguration;
import williamnogueira.dev.orchestrator.domain.factory.PaymentSagaFactory;
import williamnogueira.dev.orchestrator.domain.factory.RefundSagaFactory;
import williamnogueira.dev.orchestrator.domain.model.SagaStepEntity;
import williamnogueira.dev.orchestrator.domain.model.dto.StepCommand;
import williamnogueira.dev.orchestrator.domain.model.dto.StepReply;
import williamnogueira.dev.orchestrator.domain.model.enums.SagaStatus;
import williamnogueira.dev.orchestrator.domain.model.enums.StepStatus;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.tuple;

@SpringBootTest
@Import({TestcontainersConfiguration.class, RecordingDispatcherConfiguration.class})
class SagaOrchestratorTest extends SagaEngineTestSupport {

    private static final RefundSagaFactory REFUND_FACTORY = new RefundSagaFactory();

    @Test
    @DisplayName("a payment saga runs all three steps to COMPLETED")
    void happyPathRunsAllStepsToCompleted() {
        var sagaId = sagaOrchestrator.start(paymentSaga()).getId();

        assertThat(commandDispatcher.commands()).hasSize(1);
        assertThat(commandDispatcher.lastCommand().stepName()).isEqualTo(PaymentSagaFactory.AUTHORIZE_FUNDS);
        assertThat(commandDispatcher.lastCommand().payload()).contains("42");

        sagaOrchestrator.onReply(StepReply.success(sagaId, commandDispatcher.lastCommand().stepId()));
        assertThat(commandDispatcher.lastCommand().stepName()).isEqualTo(PaymentSagaFactory.CAPTURE_PAYMENT);

        sagaOrchestrator.onReply(StepReply.success(sagaId, commandDispatcher.lastCommand().stepId()));
        assertThat(commandDispatcher.lastCommand().stepName()).isEqualTo(PaymentSagaFactory.POST_TO_LEDGER);

        sagaOrchestrator.onReply(StepReply.success(sagaId, commandDispatcher.lastCommand().stepId()));

        var saga = sagaRepository.findWithStepsById(sagaId).orElseThrow();
        assertThat(saga.getStatus()).isEqualTo(SagaStatus.COMPLETED);
        assertThat(saga.getSteps())
                .hasSize(3)
                .allSatisfy(step -> {
                    assertThat(step.getStatus()).isEqualTo(StepStatus.COMPLETED);
                    assertThat(step.getAttempts()).isEqualTo(1);
                    assertThat(step.getDispatchedAt()).isNotNull();
                });
        assertThat(commandDispatcher.commands()).hasSize(3);
    }

    @Test
    @DisplayName("only one step is in flight at a time, in definition order")
    void stepsRunStrictlyInOrderOneAtATime() {
        var sagaId = sagaOrchestrator.start(paymentSaga()).getId();

        var saga = sagaRepository.findWithStepsById(sagaId).orElseThrow();
        assertThat(saga.getStatus()).isEqualTo(SagaStatus.STARTED);
        assertThat(saga.getSteps())
                .extracting(SagaStepEntity::getSequence, SagaStepEntity::getStatus)
                .containsExactly(
                        tuple(0, StepStatus.RUNNING),
                        tuple(1, StepStatus.PENDING),
                        tuple(2, StepStatus.PENDING));
    }

    @Test
    @DisplayName("a failure with nothing completed ends COMPENSATED with no compensation dispatches")
    void failureWithNothingCompletedEndsCompensated() {
        var sagaId = sagaOrchestrator.start(paymentSaga()).getId();

        sagaOrchestrator.onReply(StepReply.failure(sagaId, commandDispatcher.lastCommand().stepId(), "card declined"));

        var saga = sagaRepository.findWithStepsById(sagaId).orElseThrow();
        assertThat(saga.getStatus()).isEqualTo(SagaStatus.COMPENSATED);
        assertThat(saga.getSteps().getFirst().getStatus()).isEqualTo(StepStatus.FAILED);
        assertThat(saga.getSteps().get(1).getStatus()).isEqualTo(StepStatus.PENDING);
        assertThat(commandDispatcher.commands()).hasSize(1);
    }

    @Test
    @DisplayName("a failure compensates the completed steps in reverse order")
    void failureCompensatesCompletedStepsInReverse() {
        var sagaId = sagaOrchestrator.start(paymentSaga()).getId();

        sagaOrchestrator.onReply(StepReply.success(sagaId, commandDispatcher.lastCommand().stepId()));
        sagaOrchestrator.onReply(StepReply.success(sagaId, commandDispatcher.lastCommand().stepId()));
        sagaOrchestrator.onReply(StepReply.failure(sagaId, commandDispatcher.lastCommand().stepId(), "ledger unavailable"));

        assertThat(commandDispatcher.lastCommand().stepName()).isEqualTo(PaymentSagaFactory.REFUND);
        sagaOrchestrator.onReply(StepReply.success(sagaId, commandDispatcher.lastCommand().stepId()));

        assertThat(commandDispatcher.lastCommand().stepName()).isEqualTo(PaymentSagaFactory.VOID_AUTHORIZATION);
        sagaOrchestrator.onReply(StepReply.success(sagaId, commandDispatcher.lastCommand().stepId()));

        var saga = sagaRepository.findWithStepsById(sagaId).orElseThrow();
        assertThat(saga.getStatus()).isEqualTo(SagaStatus.COMPENSATED);
        assertThat(saga.getSteps())
                .extracting(SagaStepEntity::getStatus)
                .containsExactly(StepStatus.COMPENSATED, StepStatus.COMPENSATED, StepStatus.FAILED);
        assertThat(commandDispatcher.commands())
                .extracting(StepCommand::stepName)
                .containsExactly(
                        PaymentSagaFactory.AUTHORIZE_FUNDS,
                        PaymentSagaFactory.CAPTURE_PAYMENT,
                        PaymentSagaFactory.POST_TO_LEDGER,
                        PaymentSagaFactory.REFUND,
                        PaymentSagaFactory.VOID_AUTHORIZATION);
    }

    @Test
    @DisplayName("a failing compensation fails the saga for manual intervention")
    void failedCompensationFailsTheSaga() {
        var sagaId = sagaOrchestrator.start(paymentSaga()).getId();

        sagaOrchestrator.onReply(StepReply.success(sagaId, commandDispatcher.lastCommand().stepId()));
        sagaOrchestrator.onReply(StepReply.failure(sagaId, commandDispatcher.lastCommand().stepId(), "capture declined"));

        assertThat(commandDispatcher.lastCommand().stepName()).isEqualTo(PaymentSagaFactory.VOID_AUTHORIZATION);
        sagaOrchestrator.onReply(StepReply.failure(sagaId, commandDispatcher.lastCommand().stepId(), "void rejected"));

        var saga = sagaRepository.findWithStepsById(sagaId).orElseThrow();
        assertThat(saga.getStatus()).isEqualTo(SagaStatus.FAILED);
        assertThat(saga.getSteps())
                .extracting(SagaStepEntity::getStatus)
                .containsExactly(StepStatus.COMPENSATION_FAILED, StepStatus.FAILED, StepStatus.PENDING);
    }

    @Test
    @DisplayName("a manual retry resumes a failed compensation and grants one more attempt")
    void manualRetryResumesFailedCompensation() {
        var sagaId = sagaOrchestrator.start(paymentSaga()).getId();
        sagaOrchestrator.onReply(StepReply.success(sagaId, commandDispatcher.lastCommand().stepId()));
        sagaOrchestrator.onReply(StepReply.failure(sagaId, commandDispatcher.lastCommand().stepId(), "capture declined"));
        sagaOrchestrator.onReply(StepReply.failure(sagaId, commandDispatcher.lastCommand().stepId(), "void rejected"));

        var failed = sagaRepository.findWithStepsById(sagaId).orElseThrow();
        assertThat(failed.getStatus()).isEqualTo(SagaStatus.FAILED);
        assertThat(failed.getSteps().getFirst().getStatus()).isEqualTo(StepStatus.COMPENSATION_FAILED);

        sagaOrchestrator.retry(sagaId);
        assertThat(commandDispatcher.lastCommand().stepName()).isEqualTo(PaymentSagaFactory.VOID_AUTHORIZATION);
        sagaOrchestrator.onReply(StepReply.success(sagaId, commandDispatcher.lastCommand().stepId()));

        var saga = sagaRepository.findWithStepsById(sagaId).orElseThrow();
        assertThat(saga.getStatus()).isEqualTo(SagaStatus.COMPENSATED);
        assertThat(saga.getSteps().getFirst().getStatus()).isEqualTo(StepStatus.COMPENSATED);
    }

    @Test
    @DisplayName("a manual retry is rejected unless the saga is FAILED")
    void manualRetryIsRejectedUnlessSagaFailed() {
        var sagaId = sagaOrchestrator.start(paymentSaga()).getId();

        assertThatIllegalStateException()
                .isThrownBy(() -> sagaOrchestrator.retry(sagaId))
                .withMessageContaining("only FAILED sagas");
    }

    @Test
    @DisplayName("force-compensate abandons the in-flight step and rolls back the completed ones")
    void forceCompensateAbandonsInFlightStepAndRollsBack() {
        var sagaId = sagaOrchestrator.start(paymentSaga()).getId();
        sagaOrchestrator.onReply(StepReply.success(sagaId, commandDispatcher.lastCommand().stepId()));

        sagaOrchestrator.forceCompensate(sagaId);

        assertThat(commandDispatcher.lastCommand().stepName()).isEqualTo(PaymentSagaFactory.VOID_AUTHORIZATION);
        sagaOrchestrator.onReply(StepReply.success(sagaId, commandDispatcher.lastCommand().stepId()));

        var saga = sagaRepository.findWithStepsById(sagaId).orElseThrow();
        assertThat(saga.getStatus()).isEqualTo(SagaStatus.COMPENSATED);
        assertThat(saga.getSteps())
                .extracting(SagaStepEntity::getStatus)
                .containsExactly(StepStatus.COMPENSATED, StepStatus.FAILED, StepStatus.PENDING);
    }

    @Test
    @DisplayName("a duplicate reply causes no second dispatch")
    void duplicateReplyIsIgnored() {
        var sagaId = sagaOrchestrator.start(paymentSaga()).getId();
        var firstStepId = commandDispatcher.lastCommand().stepId();

        sagaOrchestrator.onReply(StepReply.success(sagaId, firstStepId));
        sagaOrchestrator.onReply(StepReply.success(sagaId, firstStepId));

        var saga = sagaRepository.findWithStepsById(sagaId).orElseThrow();
        assertThat(saga.getStatus()).isEqualTo(SagaStatus.STARTED);
        assertThat(commandDispatcher.commands()).hasSize(2);
    }

    @Test
    @DisplayName("the engine runs any saga definition, not just payment")
    void engineIsDomainAgnosticAcrossDefinitions() {
        var sagaId = sagaOrchestrator
                .start(REFUND_FACTORY.create(MAPPER.readTree("{\"paymentId\":\"p-1\"}")))
                .getId();

        sagaOrchestrator.onReply(StepReply.success(sagaId, commandDispatcher.lastCommand().stepId()));
        sagaOrchestrator.onReply(StepReply.success(sagaId, commandDispatcher.lastCommand().stepId()));

        var saga = sagaRepository.findWithStepsById(sagaId).orElseThrow();
        assertThat(saga.getStatus()).isEqualTo(SagaStatus.COMPLETED);
        assertThat(commandDispatcher.commands())
                .extracting(StepCommand::stepName)
                .containsExactly(RefundSagaFactory.ISSUE_REFUND, RefundSagaFactory.REVERSE_LEDGER_ENTRY);
    }

    @Test
    @DisplayName("saga outcomes are recorded as metrics")
    void sagaOutcomesAreRecordedAsMetrics(@Autowired MeterRegistry meterRegistry) {
        var startedBefore = counterValue(meterRegistry, "saga.started", "type", "payment");
        var completedBefore = counterValue(meterRegistry, "saga.ended", "type", "payment", "outcome", "completed");

        var sagaId = sagaOrchestrator.start(paymentSaga()).getId();
        sagaOrchestrator.onReply(StepReply.success(sagaId, commandDispatcher.lastCommand().stepId()));
        sagaOrchestrator.onReply(StepReply.success(sagaId, commandDispatcher.lastCommand().stepId()));
        sagaOrchestrator.onReply(StepReply.success(sagaId, commandDispatcher.lastCommand().stepId()));

        assertThat(counterValue(meterRegistry, "saga.started", "type", "payment")).isEqualTo(startedBefore + 1);
        assertThat(counterValue(meterRegistry, "saga.ended", "type", "payment", "outcome", "completed"))
                .isEqualTo(completedBefore + 1);
    }

    private static double counterValue(MeterRegistry meterRegistry, String name, String... tags) {
        var counter = meterRegistry.find(name).tags(tags).counter();
        return counter == null ? 0 : counter.count();
    }

    @Test
    @DisplayName("a reply for an unknown saga is ignored without error")
    void replyForUnknownSagaIsIgnored() {
        assertThatCode(() -> sagaOrchestrator.onReply(StepReply.success(UUID.randomUUID(), UUID.randomUUID())))
                .doesNotThrowAnyException();
    }

}
