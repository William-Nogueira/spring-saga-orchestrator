package williamnogueira.dev.orchestrator.domain;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

@Getter
@AllArgsConstructor
public class SagaEntity {
    private final String name;
    List<SagaStep> steps;
}
