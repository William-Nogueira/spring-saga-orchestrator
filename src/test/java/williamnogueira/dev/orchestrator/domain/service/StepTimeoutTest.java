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
import williamnogueira.dev.orchestrator.domain.model.SagaEntity;
import williamnogueira.dev.orchestrator.domain.model.SagaRepository;
import williamnogueira.dev.orchestrator.domain.model.dto.StepCommand;
import williamnogueira.dev.orchestrator.domain.model.dto.StepReply;
import williamnogueira.dev.orchestrator.domain.model.enums.SagaStatus;
import williamnogueira.dev.orchestrator.domain.model.enums.StepStatus;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "saga.step-timeout=PT1S")
@Import(TestcontainersConfiguration.class)
class StepTimeoutTest {

    private static final JsonMapper MAPPER = new JsonMapper();
    private static final PaymentSagaFactory PAYMENT_FACTORY = new PaymentSagaFactory();

    @Autowired
    private SagaOrchestrator sagaOrchestrator;

    @Autowired
    private SagaRepository sagaRepository;

    @Autowired
    private RecordingCommandDispatcher commandDispatcher;

    @TestConfiguration(proxyBeanMethods = false)
    static class NeverReplyingDispatcherConfiguration {

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
    @DisplayName("a step with no reply times out and rolls the saga back")
    void missingReplyTimesOutAndCompensatesTheSaga() throws InterruptedException {
        var sagaId = sagaOrchestrator.start(paymentSaga()).getId();

        var saga = awaitStatus(sagaId, SagaStatus.COMPENSATED);

        assertThat(saga.getSteps().getFirst().getStatus()).isEqualTo(StepStatus.FAILED);
        assertThat(saga.getSteps().get(1).getStatus()).isEqualTo(StepStatus.PENDING);
        assertThat(saga.getSteps().get(2).getStatus()).isEqualTo(StepStatus.PENDING);
        assertThat(commandDispatcher.commands()).hasSize(1);
    }

    @Test
    @DisplayName("a reply arriving after the timeout is ignored")
    void replyArrivingAfterTimeoutIsIgnored() throws InterruptedException {
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
    void compensationTimeoutFailsTheSaga() throws InterruptedException {
        var sagaId = sagaOrchestrator.start(paymentSaga()).getId();
        sagaOrchestrator.onReply(StepReply.success(sagaId, commandDispatcher.lastCommand().stepId()));

        var saga = awaitStatus(sagaId, SagaStatus.FAILED);

        assertThat(saga.getSteps().getFirst().getStatus()).isEqualTo(StepStatus.FAILED);
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
    void timelyReplyIsNotAffectedByTheTimeoutCheck() throws InterruptedException {
        var sagaId = sagaOrchestrator.start(paymentSaga()).getId();

        sagaOrchestrator.onReply(StepReply.success(sagaId, commandDispatcher.lastCommand().stepId()));
        sagaOrchestrator.onReply(StepReply.success(sagaId, commandDispatcher.lastCommand().stepId()));
        sagaOrchestrator.onReply(StepReply.success(sagaId, commandDispatcher.lastCommand().stepId()));

        Thread.sleep(1500);

        var saga = sagaRepository.findWithStepsById(sagaId).orElseThrow();
        assertThat(saga.getStatus()).isEqualTo(SagaStatus.COMPLETED);
    }

    private static SagaEntity paymentSaga() {
        return PAYMENT_FACTORY.create(MAPPER.readTree("{\"orderId\":\"42\",\"amount\":100,\"currency\":\"BRL\"}"));
    }

    private SagaEntity awaitStatus(UUID sagaId, SagaStatus expected) throws InterruptedException {
        var deadline = Instant.now().plusSeconds(10);
        while (Instant.now().isBefore(deadline)) {
            var saga = sagaRepository.findWithStepsById(sagaId).orElseThrow();
            if (saga.getStatus() == expected) {
                return saga;
            }
            Thread.sleep(100);
        }

        throw new AssertionError("saga %s did not reach %s in time".formatted(sagaId, expected));
    }
}
