package williamnogueira.dev.orchestrator.domain.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.util.ReflectionTestUtils;
import williamnogueira.dev.orchestrator.TestcontainersConfiguration;
import williamnogueira.dev.orchestrator.domain.factory.PaymentSagaFactory;
import williamnogueira.dev.orchestrator.domain.model.dto.StepCommand;
import williamnogueira.dev.orchestrator.domain.model.enums.StepStatus;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import({TestcontainersConfiguration.class, RecordingDispatcherConfiguration.class})
class SagaRecoverySweeperTest extends SagaEngineTestSupport {

    private static final Instant FIXED_NOW = Instant.parse("2100-01-01T10:00:00Z");

    @Autowired
    private SagaRecoverySweeper sagaRecoverySweeper;

    @TestConfiguration(proxyBeanMethods = false)
    static class FixedClockConfiguration {

        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(FIXED_NOW, ZoneOffset.UTC);
        }
    }

    @Test
    @DisplayName("the sweeper re-drives a step whose timer was lost to a restart")
    void sweeperRedispatchesStuckStep() {
        var sagaId = sagaOrchestrator.start(paymentSaga()).getId();
        var saga = sagaRepository.findWithStepsById(sagaId).orElseThrow();
        ReflectionTestUtils.setField(saga.getSteps().getFirst(), "dispatchedAt",
                FIXED_NOW.minus(Duration.ofMinutes(30)));
        sagaRepository.save(saga);

        sagaRecoverySweeper.sweep();

        var recovered = sagaRepository.findWithStepsById(sagaId).orElseThrow();
        assertThat(recovered.getSteps().getFirst().getStatus()).isEqualTo(StepStatus.RUNNING);
        assertThat(recovered.getSteps().getFirst().getAttempts()).isEqualTo(2);
        assertThat(commandDispatcher.commands())
                .extracting(StepCommand::stepName)
                .containsExactly(PaymentSagaFactory.AUTHORIZE_FUNDS, PaymentSagaFactory.AUTHORIZE_FUNDS);
    }

    @Test
    @DisplayName("the sweeper leaves steps within their deadline alone")
    void sweeperLeavesFreshStepsAlone() {
        var sagaId = sagaOrchestrator.start(paymentSaga()).getId();

        sagaRecoverySweeper.sweep();

        var saga = sagaRepository.findWithStepsById(sagaId).orElseThrow();
        assertThat(saga.getSteps().getFirst().getAttempts()).isEqualTo(1);
        assertThat(commandDispatcher.commands()).hasSize(1);
    }
}
