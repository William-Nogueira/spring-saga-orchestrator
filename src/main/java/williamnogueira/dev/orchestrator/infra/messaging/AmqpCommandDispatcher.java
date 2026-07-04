package williamnogueira.dev.orchestrator.infra.messaging;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import williamnogueira.dev.orchestrator.domain.model.dto.StepCommand;
import williamnogueira.dev.orchestrator.infra.config.RabbitConfig;

@Component
@RequiredArgsConstructor
@Slf4j
public class AmqpCommandDispatcher implements CommandDispatcher {

    private final RabbitTemplate rabbitTemplate;

    @Override
    public void dispatch(StepCommand command) {
        log.info("dispatching step '{}' of saga {}", command.stepName(), command.sagaId());
        rabbitTemplate.convertAndSend(RabbitConfig.COMMAND_EXCHANGE, command.stepName(), command);
    }
}
