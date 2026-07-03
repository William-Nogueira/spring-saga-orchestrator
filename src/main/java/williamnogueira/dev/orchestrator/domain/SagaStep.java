package williamnogueira.dev.orchestrator.domain;

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
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "saga_step")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SagaStep {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "saga_id")
    private SagaEntity saga;

    private int sequence;
    private String name;
    private String compensation;

    @Setter
    @Enumerated(EnumType.STRING)
    private StepStatus status;

    @Setter
    private int attempts;

    @Setter
    private Instant dispatchedAt;

    SagaStep(SagaEntity saga, int sequence, String name, String compensation) {
        this.saga = saga;
        this.sequence = sequence;
        this.name = name;
        this.compensation = compensation;
        this.status = StepStatus.PENDING;
    }
}
