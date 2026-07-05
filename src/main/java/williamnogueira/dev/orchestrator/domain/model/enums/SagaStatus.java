package williamnogueira.dev.orchestrator.domain.model.enums;

import java.util.EnumSet;
import java.util.Set;

public enum SagaStatus {
    STARTED,
    COMPLETED,
    FAILED,
    COMPENSATING,
    COMPENSATED;

    public Set<SagaStatus> allowedTransitions() {
        return switch (this) {
            case STARTED -> EnumSet.of(COMPLETED, COMPENSATING, FAILED);
            case COMPENSATING -> EnumSet.of(COMPENSATED, FAILED);
            case FAILED -> EnumSet.of(COMPENSATING);
            case COMPENSATED, COMPLETED -> EnumSet.noneOf(SagaStatus.class);
        };
    }
}
