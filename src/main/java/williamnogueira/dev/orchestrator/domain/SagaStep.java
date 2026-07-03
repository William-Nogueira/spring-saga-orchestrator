package williamnogueira.dev.orchestrator.domain;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class SagaStep {
    private final int sequence;
    private final String name;
    private final String compensation;
    private final StepStatus status;
}
