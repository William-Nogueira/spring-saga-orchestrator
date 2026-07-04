package williamnogueira.dev.orchestrator.infra.messaging;

import williamnogueira.dev.orchestrator.domain.model.dto.StepCommand;

public interface CommandDispatcher {
    void dispatch(StepCommand command);
}
