package williamnogueira.dev.orchestrator.domain;

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
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
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

    @Setter
    @Enumerated(EnumType.STRING)
    private SagaStatus status;

    @OneToMany(mappedBy = "saga", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sequence")
    private final List<SagaStep> steps = new ArrayList<>();

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
        steps.add(new SagaStep(this, steps.size(), name, compensation));
    }
}
