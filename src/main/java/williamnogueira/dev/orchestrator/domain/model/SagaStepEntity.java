package williamnogueira.dev.orchestrator.domain.model;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import williamnogueira.dev.orchestrator.domain.model.enums.StepStatus;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "saga_step")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SagaStepEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "saga_id")
    private SagaEntity saga;

    private int sequence;
    private String name;
    private String compensation;

    @Enumerated(EnumType.STRING)
    private StepStatus status;

    private int attempts;
    private Instant dispatchedAt;

    SagaStepEntity(SagaEntity saga, int sequence, String name, String compensation) {
        this.saga = saga;
        this.sequence = sequence;
        this.name = name;
        this.compensation = compensation;
        this.status = StepStatus.PENDING;
    }

    public void transitionTo(StepStatus target) {
        if (!status.allowedTransitions().contains(target)) {
            throw new IllegalStateException("step '%s' cannot transition from %s to %s".formatted(name, status, target));
        }

        this.status = target;
    }

    public void markDispatched(Instant when) {
        transitionTo(StepStatus.RUNNING);
        this.attempts++;
        this.dispatchedAt = when;
    }

    public void markCompensating(Instant when) {
        transitionTo(StepStatus.COMPENSATING);
        this.attempts++;
        this.dispatchedAt = when;
    }
}
