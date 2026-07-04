package williamnogueira.dev.orchestrator.domain.service;

import williamnogueira.dev.orchestrator.domain.model.dto.StepCommand;
import williamnogueira.dev.orchestrator.infra.messaging.CommandDispatcher;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class RecordingCommandDispatcher implements CommandDispatcher {

    private final List<StepCommand> commands = new CopyOnWriteArrayList<>();

    @Override
    public void dispatch(StepCommand command) {
        commands.add(command);
    }

    public List<StepCommand> commands() {
        return commands;
    }

    public StepCommand lastCommand() {
        return commands.getLast();
    }

    public void clear() {
        commands.clear();
    }
}
