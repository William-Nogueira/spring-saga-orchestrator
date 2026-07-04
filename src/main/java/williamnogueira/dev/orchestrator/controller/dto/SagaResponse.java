package williamnogueira.dev.orchestrator.controller.dto;

import williamnogueira.dev.orchestrator.domain.model.SagaEntity;
import williamnogueira.dev.orchestrator.domain.model.enums.SagaStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record SagaResponse(
        UUID id,
        String name,
        SagaStatus status,
        Instant createdAt,
        Instant updatedAt,
        List<StepView> steps) {

    public static SagaResponse from(SagaEntity saga) {
        return new SagaResponse(
                saga.getId(),
                saga.getName(),
                saga.getStatus(),
                saga.getCreatedAt(),
                saga.getUpdatedAt(),
                saga.getSteps().stream().map(StepView::from).toList());
    }
}
