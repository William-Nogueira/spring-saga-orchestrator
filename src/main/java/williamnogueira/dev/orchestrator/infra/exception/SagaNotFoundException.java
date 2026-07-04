package williamnogueira.dev.orchestrator.infra.exception;

import java.util.UUID;

public class SagaNotFoundException extends RuntimeException {

    public SagaNotFoundException(UUID id) {
        super("saga %s not found".formatted(id));
    }
}
