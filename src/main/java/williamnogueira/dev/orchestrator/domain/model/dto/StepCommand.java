package williamnogueira.dev.orchestrator.domain.model.dto;

import java.util.UUID;

public record StepCommand(UUID sagaId, UUID stepId, String stepName, String payload) {
}
