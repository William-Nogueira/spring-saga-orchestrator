package williamnogueira.dev.orchestrator.infra.messaging;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import williamnogueira.dev.orchestrator.domain.service.SagaOrchestrator;
import williamnogueira.dev.orchestrator.domain.model.dto.StepReply;
import williamnogueira.dev.orchestrator.infra.config.RabbitConfig;

@Component
@RequiredArgsConstructor
@Slf4j
public class SagaReplyListener {

    private final SagaOrchestrator sagaOrchestrator;

    @RabbitListener(queues = RabbitConfig.REPLY_QUEUE)
    public void onReply(StepReply reply) {
        log.debug("reply received for saga {} step {}: success={}", reply.sagaId(), reply.stepId(), reply.success());
        sagaOrchestrator.onReply(reply);
    }
}
