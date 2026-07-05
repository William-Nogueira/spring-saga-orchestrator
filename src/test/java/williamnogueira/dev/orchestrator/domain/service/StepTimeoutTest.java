package williamnogueira.dev.orchestrator.domain.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import williamnogueira.dev.orchestrator.TestcontainersConfiguration;
import williamnogueira.dev.orchestrator.domain.factory.PaymentSagaFactory;
import williamnogueira.dev.orchestrator.domain.model.dto.StepCommand;
import williamnogueira.dev.orchestrator.domain.model.dto.StepReply;
import williamnogueira.dev.orchestrator.domain.model.enums.SagaStatus;
import williamnogueira.dev.orchestrator.domain.model.enums.StepStatus;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest(properties = {"saga.step-timeout=PT1S", "saga.step-max-attempts=1"})
@Import({TestcontainersConfiguration.class, RecordingDispatcherConfiguration.class})
class StepTimeoutTest extends SagaEngineTestSupport {

    @Test
    @DisplayName("a step with no reply times out and rolls the saga back")
    void missingReplyTimesOutAndCompensatesTheSaga() {
        var sagaId = sagaOrchestrator.start(paymentSaga()).getId();

        var saga = awaitStatus(sagaId, SagaStatus.COMPENSATED);

        assertThat(saga.getSteps().getFirst().getStatus()).isEqualTo(StepStatus.FAILED);
        assertThat(saga.getSteps().get(1).getStatus()).isEqualTo(StepStatus.PENDING);
        assertThat(saga.getSteps().get(2).getStatus()).isEqualTo(StepStatus.PENDING);
        assertThat(commandDispatcher.commands()).hasSize(1);
    }

    @Test
    @DisplayName("a reply arriving after the timeout is ignored")
    void replyArrivingAfterTimeoutIsIgnored() {
        var sagaId = sagaOrchestrator.start(paymentSaga()).getId();
        var stepId = commandDispatcher.lastCommand().stepId();

        awaitStatus(sagaId, SagaStatus.COMPENSATED);
        sagaOrchestrator.onReply(StepReply.success(sagaId, stepId));

        var saga = sagaRepository.findWithStepsById(sagaId).orElseThrow();
        assertThat(saga.getStatus()).isEqualTo(SagaStatus.COMPENSATED);
        assertThat(saga.getSteps().getFirst().getStatus()).isEqualTo(StepStatus.FAILED);
        assertThat(commandDispatcher.commands()).hasSize(1);
    }

    @Test
    @DisplayName("a compensation with no reply times out and fails the saga for manual intervention")
    void compensationTimeoutFailsTheSaga() {
        var sagaId = sagaOrchestrator.start(paymentSaga()).getId();
        sagaOrchestrator.onReply(StepReply.success(sagaId, commandDispatcher.lastCommand().stepId()));

        var saga = awaitStatus(sagaId, SagaStatus.FAILED);

        assertThat(saga.getSteps().getFirst().getStatus()).isEqualTo(StepStatus.COMPENSATION_FAILED);
        assertThat(saga.getSteps().get(1).getStatus()).isEqualTo(StepStatus.FAILED);
        assertThat(saga.getSteps().get(2).getStatus()).isEqualTo(StepStatus.PENDING);
        assertThat(commandDispatcher.commands())
                .extracting(StepCommand::stepName)
                .containsExactly(
                        PaymentSagaFactory.AUTHORIZE_FUNDS,
                        PaymentSagaFactory.CAPTURE_PAYMENT,
                        PaymentSagaFactory.VOID_AUTHORIZATION);
    }

    @Test
    @DisplayName("timeout checks fire and no-op on steps that replied in time")
    void timelyReplyIsNotAffectedByTheTimeoutCheck() {
        var sagaId = sagaOrchestrator.start(paymentSaga()).getId();

        sagaOrchestrator.onReply(StepReply.success(sagaId, commandDispatcher.lastCommand().stepId()));
        sagaOrchestrator.onReply(StepReply.success(sagaId, commandDispatcher.lastCommand().stepId()));
        sagaOrchestrator.onReply(StepReply.success(sagaId, commandDispatcher.lastCommand().stepId()));

        await().during(Duration.ofMillis(1500)).atMost(Duration.ofSeconds(5))
                .until(() -> sagaRepository.findWithStepsById(sagaId).orElseThrow().getStatus() == SagaStatus.COMPLETED);
    }
}
