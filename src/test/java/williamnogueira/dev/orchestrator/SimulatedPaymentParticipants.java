package williamnogueira.dev.orchestrator;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import tools.jackson.databind.ObjectMapper;
import williamnogueira.dev.orchestrator.domain.model.dto.StepCommand;
import williamnogueira.dev.orchestrator.domain.model.dto.StepReply;
import williamnogueira.dev.orchestrator.infra.config.RabbitConfig;

import java.math.BigDecimal;

@RequiredArgsConstructor
@Slf4j
public class SimulatedPaymentParticipants {

    static final BigDecimal AUTHORIZATION_LIMIT = new BigDecimal("10000");

    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;

    @RabbitListener(queues = RabbitConfig.AUTHORIZE_FUNDS_QUEUE)
    void authorize(StepCommand command) {
        var amount = objectMapper.readTree(command.payload()).path("amount").decimalValue();
        var reply = amount.compareTo(AUTHORIZATION_LIMIT) > 0
                ? StepReply.failure(command.sagaId(), command.stepId(), "amount exceeds authorization limit")
                : StepReply.success(command.sagaId(), command.stepId());
        log.info("simulated authorization for saga {}: success={}", command.sagaId(), reply.success());
        reply(reply);
    }

    @RabbitListener(queues = RabbitConfig.CAPTURE_PAYMENT_QUEUE)
    void capture(StepCommand command) {
        log.info("simulated capture for saga {}", command.sagaId());
        reply(StepReply.success(command.sagaId(), command.stepId()));
    }

    @RabbitListener(queues = RabbitConfig.POST_TO_LEDGER_QUEUE)
    void postToLedger(StepCommand command) {
        log.info("simulated ledger posting for saga {}", command.sagaId());
        reply(StepReply.success(command.sagaId(), command.stepId()));
    }

    private void reply(StepReply reply) {
        rabbitTemplate.convertAndSend(RabbitConfig.REPLY_EXCHANGE, RabbitConfig.REPLY_ROUTING_KEY, reply);
    }
}
