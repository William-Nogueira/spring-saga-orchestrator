package williamnogueira.dev.orchestrator;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import tools.jackson.databind.json.JsonMapper;
import williamnogueira.dev.orchestrator.domain.factory.PaymentSagaFactory;
import williamnogueira.dev.orchestrator.domain.model.SagaEntity;
import williamnogueira.dev.orchestrator.domain.service.SagaOrchestrator;
import williamnogueira.dev.orchestrator.domain.model.SagaRepository;
import williamnogueira.dev.orchestrator.domain.model.enums.SagaStatus;
import williamnogueira.dev.orchestrator.domain.model.enums.StepStatus;

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest
@Import({TestcontainersConfiguration.class, SimulatedPaymentParticipants.class})
class SagaEndToEndTest {

    private static final JsonMapper MAPPER = new JsonMapper();
    private static final PaymentSagaFactory PAYMENT_FACTORY = new PaymentSagaFactory();

    @Autowired
    private SagaOrchestrator sagaOrchestrator;

    @Autowired
    private SagaRepository sagaRepository;

    @AfterEach
    void cleanUp() {
        sagaRepository.deleteAll();
    }

    @Test
    @DisplayName("a saga completes end to end over real rabbitmq queues")
    void sagaCompletesPurelyOverRabbitMq() {
        var sagaId = sagaOrchestrator
                .start(PAYMENT_FACTORY.create(MAPPER.readTree("{\"orderId\":\"e2e-1\",\"amount\":249.90,\"currency\":\"BRL\"}")))
                .getId();

        var saga = awaitTerminalState(sagaId);

        assertThat(saga.getStatus()).isEqualTo(SagaStatus.COMPLETED);
        assertThat(saga.getSteps())
                .hasSize(3)
                .allSatisfy(step -> {
                    assertThat(step.getStatus()).isEqualTo(StepStatus.COMPLETED);
                    assertThat(step.getAttempts()).isEqualTo(1);
                });
    }

    @Test
    @DisplayName("a declined authorization rolls the saga back with nothing to compensate")
    void authorizationDeclineCompensatesTheSaga() {
        var sagaId = sagaOrchestrator
                .start(PAYMENT_FACTORY.create(MAPPER.readTree("{\"orderId\":\"e2e-2\",\"amount\":999999,\"currency\":\"BRL\"}")))
                .getId();

        var saga = awaitTerminalState(sagaId);

        assertThat(saga.getStatus()).isEqualTo(SagaStatus.COMPENSATED);
        assertThat(saga.getSteps().getFirst().getStatus()).isEqualTo(StepStatus.FAILED);
        assertThat(saga.getSteps().get(1).getStatus()).isEqualTo(StepStatus.PENDING);
        assertThat(saga.getSteps().get(2).getStatus()).isEqualTo(StepStatus.PENDING);
    }

    @Test
    @DisplayName("a declined capture voids the authorization over real queues")
    void captureDeclineCompensatesTheAuthorization() {
        var sagaId = sagaOrchestrator
                .start(PAYMENT_FACTORY.create(MAPPER.readTree(
                        "{\"orderId\":\"e2e-3\",\"amount\":100,\"currency\":\"BRL\",\"failCapture\":true}")))
                .getId();

        var saga = awaitTerminalState(sagaId);

        assertThat(saga.getStatus()).isEqualTo(SagaStatus.COMPENSATED);
        assertThat(saga.getSteps().getFirst().getStatus()).isEqualTo(StepStatus.COMPENSATED);
        assertThat(saga.getSteps().get(1).getStatus()).isEqualTo(StepStatus.FAILED);
        assertThat(saga.getSteps().get(2).getStatus()).isEqualTo(StepStatus.PENDING);
    }

    private SagaEntity awaitTerminalState(UUID sagaId) {
        await().atMost(Duration.ofSeconds(15))
                .pollInterval(Duration.ofMillis(200))
                .until(() -> sagaRepository.findWithStepsById(sagaId).orElseThrow()
                        .getStatus().allowedTransitions().isEmpty());

        return sagaRepository.findWithStepsById(sagaId).orElseThrow();
    }
}
