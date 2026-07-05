package williamnogueira.dev.orchestrator.domain.service;

import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import tools.jackson.databind.json.JsonMapper;
import williamnogueira.dev.orchestrator.domain.factory.PaymentSagaFactory;
import williamnogueira.dev.orchestrator.domain.model.SagaEntity;
import williamnogueira.dev.orchestrator.domain.model.SagaRepository;
import williamnogueira.dev.orchestrator.domain.model.enums.SagaStatus;

import java.time.Duration;
import java.util.UUID;

import static org.awaitility.Awaitility.await;

abstract class SagaEngineTestSupport {

    static final JsonMapper MAPPER = new JsonMapper();
    static final PaymentSagaFactory PAYMENT_FACTORY = new PaymentSagaFactory();

    @Autowired
    protected SagaOrchestrator sagaOrchestrator;

    @Autowired
    protected SagaRepository sagaRepository;

    @Autowired
    protected RecordingCommandDispatcher commandDispatcher;

    @AfterEach
    void cleanUp() {
        sagaRepository.deleteAll();
        commandDispatcher.clear();
    }

    static SagaEntity paymentSaga() {
        return PAYMENT_FACTORY.create(MAPPER.readTree("{\"orderId\":\"42\",\"amount\":100,\"currency\":\"BRL\"}"));
    }

    protected SagaEntity awaitStatus(UUID sagaId, SagaStatus expected) {
        await().atMost(Duration.ofSeconds(15))
                .pollInterval(Duration.ofMillis(100))
                .until(() -> sagaRepository.findWithStepsById(sagaId).orElseThrow().getStatus() == expected);

        return sagaRepository.findWithStepsById(sagaId).orElseThrow();
    }
}
