package williamnogueira.dev.orchestrator.domain.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import tools.jackson.databind.json.JsonMapper;
import williamnogueira.dev.orchestrator.TestcontainersConfiguration;
import williamnogueira.dev.orchestrator.domain.factory.PaymentSagaFactory;
import williamnogueira.dev.orchestrator.domain.factory.RefundSagaFactory;
import williamnogueira.dev.orchestrator.domain.model.SagaEntity;
import williamnogueira.dev.orchestrator.domain.model.SagaRepository;
import williamnogueira.dev.orchestrator.domain.model.SagaStepEntity;
import williamnogueira.dev.orchestrator.domain.model.dto.StepCommand;
import williamnogueira.dev.orchestrator.domain.model.dto.StepReply;
import williamnogueira.dev.orchestrator.domain.model.enums.SagaStatus;
import williamnogueira.dev.orchestrator.domain.model.enums.StepStatus;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.tuple;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class SagaOrchestratorTest {

    private static final JsonMapper MAPPER = new JsonMapper();
    private static final PaymentSagaFactory PAYMENT_FACTORY = new PaymentSagaFactory();
    private static final RefundSagaFactory REFUND_FACTORY = new RefundSagaFactory();

    @Autowired
    private SagaOrchestrator sagaOrchestrator;

    @Autowired
    private SagaRepository sagaRepository;

    @Autowired
    private RecordingCommandDispatcher commandDispatcher;

    @TestConfiguration(proxyBeanMethods = false)
    static class RecordingDispatcherConfiguration {

        @Bean
        @Primary
        RecordingCommandDispatcher recordingCommandDispatcher() {
            return new RecordingCommandDispatcher();
        }
    }

    @AfterEach
    void cleanUp() {
        sagaRepository.deleteAll();
        commandDispatcher.clear();
    }

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
                .containsExactly(StepStatus.FAILED, StepStatus.FAILED, StepStatus.PENDING);
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
    @DisplayName("a reply for an unknown saga is ignored without error")
    void replyForUnknownSagaIsIgnored() {
        assertThatCode(() -> sagaOrchestrator.onReply(StepReply.success(UUID.randomUUID(), UUID.randomUUID())))
                .doesNotThrowAnyException();
    }

    private static SagaEntity paymentSaga() {
        return PAYMENT_FACTORY.create(MAPPER.readTree("{\"orderId\":\"42\",\"amount\":100,\"currency\":\"BRL\"}"));
    }
}
