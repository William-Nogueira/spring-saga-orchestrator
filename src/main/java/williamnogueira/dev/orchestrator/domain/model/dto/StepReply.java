package williamnogueira.dev.orchestrator.domain.model.dto;

import java.util.UUID;

public record StepReply(UUID sagaId, UUID stepId, boolean success, String reason) {

    public static StepReply success(UUID sagaId, UUID stepId) {
        return new StepReply(sagaId, stepId, true, null);
    }

    public static StepReply failure(UUID sagaId, UUID stepId, String reason) {
        return new StepReply(sagaId, stepId, false, reason);
    }

    public boolean failure() {
        return !success;
    }
}
