package williamnogueira.dev.orchestrator.domain.model.enums;

import java.util.EnumSet;
import java.util.Set;

public enum StepStatus {
    PENDING,
    RUNNING,
    COMPLETED,
    FAILED,
    COMPENSATING,
    COMPENSATION_FAILED,
    COMPENSATED;

    public Set<StepStatus> allowedTransitions() {
        return switch (this) {
            case PENDING -> EnumSet.of(RUNNING);
            case RUNNING -> EnumSet.of(COMPLETED, FAILED);
            case COMPLETED -> EnumSet.of(COMPENSATING);
            case COMPENSATING -> EnumSet.of(COMPENSATED, COMPENSATION_FAILED);
            case COMPENSATION_FAILED -> EnumSet.of(COMPENSATING);
            case FAILED, COMPENSATED -> EnumSet.noneOf(StepStatus.class);
        };
    }
}
