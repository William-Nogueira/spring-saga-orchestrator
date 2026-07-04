package williamnogueira.dev.orchestrator.controller.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import tools.jackson.databind.JsonNode;

public record StartSagaRequest(
        @NotBlank
        String type,
        @NotNull
        JsonNode payload) {
}
