package williamnogueira.dev.orchestrator.infra.config;

import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarable;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import williamnogueira.dev.orchestrator.domain.factory.PaymentSagaFactory;

import java.util.ArrayList;
import java.util.List;

@Configuration
public class RabbitConfig {

    public static final String COMMAND_EXCHANGE = "saga.commands";
    public static final String REPLY_EXCHANGE = "saga.replies";
    public static final String REPLY_QUEUE = "saga.reply";
    public static final String REPLY_ROUTING_KEY = "saga.reply";

    public static final String COMMAND_QUEUE_PREFIX = "saga.command.";
    public static final String AUTHORIZE_FUNDS_QUEUE = COMMAND_QUEUE_PREFIX + PaymentSagaFactory.AUTHORIZE_FUNDS;
    public static final String CAPTURE_PAYMENT_QUEUE = COMMAND_QUEUE_PREFIX + PaymentSagaFactory.CAPTURE_PAYMENT;
    public static final String POST_TO_LEDGER_QUEUE = COMMAND_QUEUE_PREFIX + PaymentSagaFactory.POST_TO_LEDGER;

    @Bean
    public Declarables sagaTopology() {
        var commands = new DirectExchange(COMMAND_EXCHANGE);
        var replies = new DirectExchange(REPLY_EXCHANGE);
        var replyQueue = QueueBuilder.durable(REPLY_QUEUE).build();

        List<Declarable> declarableList = new ArrayList<>(List.of(
                commands,
                replies,
                replyQueue,
                BindingBuilder.bind(replyQueue).to(replies).with(REPLY_ROUTING_KEY)));

        for (var step : List.of(PaymentSagaFactory.AUTHORIZE_FUNDS, PaymentSagaFactory.CAPTURE_PAYMENT, PaymentSagaFactory.POST_TO_LEDGER)) {
            var queue = QueueBuilder.durable(COMMAND_QUEUE_PREFIX + step).build();
            declarableList.add(queue);
            declarableList.add(BindingBuilder.bind(queue).to(commands).with(step));
        }

        return new Declarables(declarableList);
    }

    @Bean
    public JacksonJsonMessageConverter messageConverter() {
        return new JacksonJsonMessageConverter();
    }
}
