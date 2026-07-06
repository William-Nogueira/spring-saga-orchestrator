package williamnogueira.dev.orchestrator.domain.model;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;
import williamnogueira.dev.orchestrator.TestcontainersConfiguration;
import williamnogueira.dev.orchestrator.domain.model.enums.SagaStatus;
import williamnogueira.dev.orchestrator.domain.model.enums.StepStatus;

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
    @DisplayName("a saga round-trips through postgres with its steps in order")
    void savesAndReloadsSagaWithOrderedSteps() {
        var saga = new SagaEntity("payment", "{\"orderId\":\"42\",\"amount\":100}");
        saga.addStep("authorize-funds", "void-authorization");
        saga.addStep("capture-payment", "refund");
        saga.addStep("post-to-ledger", "reverse-ledger-entry");

        var savedId = sagaRepository.save(saga).getId();

        entityManager.flush();
        entityManager.clear();

        var reloaded = sagaRepository.findById(savedId).orElseThrow();
        var mapper = new JsonMapper();
        assertThat(reloaded.getName()).isEqualTo("payment");
        assertThat(mapper.readTree(reloaded.getPayload()))
                .isEqualTo(mapper.readTree("{\"orderId\":\"42\",\"amount\":100}"));
        assertThat(reloaded.getStatus()).isEqualTo(SagaStatus.STARTED);
        assertThat(reloaded.getCreatedAt()).isNotNull();
        assertThat(reloaded.getVersion()).isNotNull();
        assertThat(reloaded.getSteps())
                .extracting(SagaStepEntity::getSequence, SagaStepEntity::getName, SagaStepEntity::getCompensation, SagaStepEntity::getStatus)
                .containsExactly(
                        tuple(0, "authorize-funds", "void-authorization", StepStatus.PENDING),
                        tuple(1, "capture-payment", "refund", StepStatus.PENDING),
                        tuple(2, "post-to-ledger", "reverse-ledger-entry", StepStatus.PENDING));
    }
}
