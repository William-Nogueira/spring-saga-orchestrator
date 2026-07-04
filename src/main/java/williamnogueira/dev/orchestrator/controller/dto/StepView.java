package williamnogueira.dev.orchestrator.controller.dto;

import williamnogueira.dev.orchestrator.domain.model.SagaStepEntity;
import williamnogueira.dev.orchestrator.domain.model.enums.StepStatus;

import java.time.Instant;

public record StepView(
        int sequence,
        String name,
        String compensation,
        StepStatus status,
        int attempts,
        Instant dispatchedAt) {

    public static StepView from(SagaStepEntity step) {
        return new StepView(
                step.getSequence(),
                step.getName(),
                step.getCompensation(),
                step.getStatus(),
                step.getAttempts(),
                step.getDispatchedAt());
    }
}
