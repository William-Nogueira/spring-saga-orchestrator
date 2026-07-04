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

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

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
    void sagaCompletesPurelyOverRabbitMq() throws InterruptedException {
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
    @DisplayName("a declined authorization fails the saga at the first step")
    void authorizationDeclineFailsTheSaga() throws InterruptedException {
        var sagaId = sagaOrchestrator
                .start(PAYMENT_FACTORY.create(MAPPER.readTree("{\"orderId\":\"e2e-2\",\"amount\":999999,\"currency\":\"BRL\"}")))
                .getId();

        var saga = awaitTerminalState(sagaId);

        assertThat(saga.getStatus()).isEqualTo(SagaStatus.FAILED);
        assertThat(saga.getSteps().getFirst().getStatus()).isEqualTo(StepStatus.FAILED);
        assertThat(saga.getSteps().get(1).getStatus()).isEqualTo(StepStatus.PENDING);
        assertThat(saga.getSteps().get(2).getStatus()).isEqualTo(StepStatus.PENDING);
    }

    private SagaEntity awaitTerminalState(UUID sagaId) throws InterruptedException {
        var deadline = Instant.now().plusSeconds(15);
        while (Instant.now().isBefore(deadline)) {
            var saga = sagaRepository.findWithStepsById(sagaId).orElseThrow();
            if (saga.getStatus().allowedTransitions().isEmpty()) {
                return saga;
            }
            Thread.sleep(200);
        }

        throw new AssertionError("saga %s did not reach a terminal state in time".formatted(sagaId));
    }
}
