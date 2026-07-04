package williamnogueira.dev.orchestrator.domain.factory;

import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import williamnogueira.dev.orchestrator.domain.model.SagaEntity;
import williamnogueira.dev.orchestrator.infra.exception.UnknownSagaTypeException;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static java.util.Objects.isNull;

@Component
public class SagaFactoryRegistry {

    private final Map<String, SagaFactory> factories;

    public SagaFactoryRegistry(List<SagaFactory> factories) {
        this.factories = factories.stream()
                .collect(Collectors.toUnmodifiableMap(SagaFactory::sagaName, Function.identity()));
    }

    public SagaEntity create(String type, JsonNode payload) {
        var factory = factories.get(type);
        if (isNull(factory)) {
            throw new UnknownSagaTypeException(type, factories.keySet());
        }

        return factory.create(payload);
    }

}
