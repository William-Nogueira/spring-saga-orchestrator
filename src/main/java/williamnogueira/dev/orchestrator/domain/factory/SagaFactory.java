package williamnogueira.dev.orchestrator.domain.factory;

import tools.jackson.databind.JsonNode;
import williamnogueira.dev.orchestrator.domain.model.SagaEntity;

import static org.apache.commons.lang3.StringUtils.isBlank;

public interface SagaFactory {

    String sagaName();
    SagaEntity create(JsonNode payload);

    default String requireText(JsonNode payload, String field) {
        var node = payload.path(field);

        if (!node.isString() || isBlank(node.stringValue())) {
            throw new IllegalArgumentException("payload field '%s' is required".formatted(field));
        }

        return node.stringValue();
    }

    default void requirePositiveNumber(JsonNode payload, String field) {
        var node = payload.path(field);

        if (!node.isNumber() || node.decimalValue().signum() <= 0) {
            throw new IllegalArgumentException("payload field '%s' must be a positive number".formatted(field));
        }
    }
}
