package williamnogueira.dev.orchestrator.domain.model;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface SagaRepository extends JpaRepository<SagaEntity, UUID> {

    @EntityGraph(attributePaths = "steps")
    Optional<SagaEntity> findWithStepsById(UUID id);
}
