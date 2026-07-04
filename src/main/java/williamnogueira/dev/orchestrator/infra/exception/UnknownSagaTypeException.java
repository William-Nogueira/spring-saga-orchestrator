package williamnogueira.dev.orchestrator.infra.exception;

import java.util.Set;

public class UnknownSagaTypeException extends RuntimeException {

    public UnknownSagaTypeException(String type, Set<String> knownTypes) {
        super("unknown saga type '%s'; known types: %s".formatted(type, knownTypes));
    }
}
