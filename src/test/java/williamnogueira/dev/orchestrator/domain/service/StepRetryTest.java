package williamnogueira.dev.orchestrator.domain.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import williamnogueira.dev.orchestrator.TestcontainersConfiguration;
import williamnogueira.dev.orchestrator.domain.factory.PaymentSagaFactory;
import williamnogueira.dev.orchestrator.domain.model.dto.StepCommand;
import williamnogueira.dev.orchestrator.domain.model.enums.SagaStatus;
import williamnogueira.dev.orchestrator.domain.model.enums.StepStatus;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {"saga.step-timeout=PT1S", "saga.step-max-attempts=2"})
@Import({TestcontainersConfiguration.class, RecordingDispatcherConfiguration.class})
class StepRetryTest extends SagaEngineTestSupport {

    @Test
    @DisplayName("a silent step is retried with backoff before the saga rolls back")
    void timeoutRetriesBeforeCompensating() {
        var sagaId = sagaOrchestrator.start(paymentSaga()).getId();

        var saga = awaitStatus(sagaId, SagaStatus.COMPENSATED);

        assertThat(saga.getSteps().getFirst().getStatus()).isEqualTo(StepStatus.FAILED);
        assertThat(saga.getSteps().getFirst().getAttempts()).isEqualTo(2);
        assertThat(commandDispatcher.commands())
                .extracting(StepCommand::stepName)
                .containsExactly(PaymentSagaFactory.AUTHORIZE_FUNDS, PaymentSagaFactory.AUTHORIZE_FUNDS);
    }
}
