package williamnogueira.dev.orchestrator.domain.model;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import williamnogueira.dev.orchestrator.domain.model.enums.StepStatus;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public interface SagaStepRepository extends JpaRepository<SagaStepEntity, UUID> {

    @EntityGraph(attributePaths = "saga")
    List<SagaStepEntity> findByStatusIn(Collection<StepStatus> statuses);
}
