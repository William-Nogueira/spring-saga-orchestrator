package williamnogueira.dev.orchestrator.domain.model;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import williamnogueira.dev.orchestrator.domain.model.enums.SagaStatus;
import williamnogueira.dev.orchestrator.domain.model.enums.StepStatus;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Entity
@Table(name = "saga")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SagaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    private String name;
    private String payload;

    @Enumerated(EnumType.STRING)
    private SagaStatus status;

    @OneToMany(mappedBy = "saga", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sequence")
    private final List<SagaStepEntity> steps = new ArrayList<>();

    @CreationTimestamp
    private Instant createdAt;

    @UpdateTimestamp
    private Instant updatedAt;

    @Version
    private Long version;

    public SagaEntity(String name, String payload) {
        this.name = name;
        this.payload = payload;
        this.status = SagaStatus.STARTED;
    }

    public void addStep(String name, String compensation) {
        steps.add(new SagaStepEntity(this, steps.size(), name, compensation));
    }

    public void transitionTo(SagaStatus target) {
        if (!status.allowedTransitions().contains(target)) {
            throw new IllegalStateException("saga %s cannot transition from %s to %s".formatted(id, status, target));
        }

        this.status = target;
    }

    public Optional<SagaStepEntity> nextPendingStep() {
        return steps.stream().filter(step -> step.getStatus() == StepStatus.PENDING).findFirst();
    }

    public Optional<SagaStepEntity> lastCompletedStep() {
        return steps.stream()
                .filter(step -> step.getStatus() == StepStatus.COMPLETED)
                .reduce((_, second) -> second);
    }

    public Optional<SagaStepEntity> findStep(UUID stepId) {
        return steps.stream().filter(step -> stepId.equals(step.getId())).findFirst();
    }
}
