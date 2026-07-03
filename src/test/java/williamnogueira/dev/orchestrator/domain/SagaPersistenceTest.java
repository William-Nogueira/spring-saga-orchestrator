package williamnogueira.dev.orchestrator.domain;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;
import williamnogueira.dev.orchestrator.TestcontainersConfiguration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class SagaPersistenceTest {

    @Autowired
    private SagaRepository sagaRepository;

    @PersistenceContext
    private EntityManager entityManager;

    @Test
    void savesAndReloadsSagaWithOrderedSteps() {
        var saga = new SagaEntity("payment", "{\"orderId\":\"42\",\"amount\":100}");
        saga.addStep("authorize-funds", "void-authorization");
        saga.addStep("capture-payment", "refund");
        saga.addStep("post-to-ledger", "reverse-ledger-entry");

        var savedId = sagaRepository.save(saga).getId();

        entityManager.flush();
        entityManager.clear();

        var reloaded = sagaRepository.findById(savedId).orElseThrow();
        assertThat(reloaded.getName()).isEqualTo("payment");
        assertThat(reloaded.getPayload()).isEqualTo("{\"orderId\":\"42\",\"amount\":100}");
        assertThat(reloaded.getStatus()).isEqualTo(SagaStatus.STARTED);
        assertThat(reloaded.getCreatedAt()).isNotNull();
        assertThat(reloaded.getVersion()).isNotNull();
        assertThat(reloaded.getSteps())
                .extracting(SagaStep::getSequence, SagaStep::getName, SagaStep::getCompensation, SagaStep::getStatus)
                .containsExactly(
                        tuple(0, "authorize-funds", "void-authorization", StepStatus.PENDING),
                        tuple(1, "capture-payment", "refund", StepStatus.PENDING),
                        tuple(2, "post-to-ledger", "reverse-ledger-entry", StepStatus.PENDING));
    }
}
