package williamnogueira.dev.orchestrator.domain.factory;

import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import williamnogueira.dev.orchestrator.domain.model.SagaEntity;

/**
 * Authorizing, capturing, and recording a payment.
 */
@Component
public class PaymentSagaFactory implements SagaFactory {

    public static final String SAGA_NAME = "payment";

    public static final String AUTHORIZE_FUNDS = "authorize-funds";
    public static final String VOID_AUTHORIZATION = "void-authorization";
    public static final String CAPTURE_PAYMENT = "capture-payment";
    public static final String REFUND = "refund";
    public static final String POST_TO_LEDGER = "post-to-ledger";
    public static final String REVERSE_LEDGER_ENTRY = "reverse-ledger-entry";

    @Override
    public String sagaName() {
        return SAGA_NAME;
    }

    @Override
    public SagaEntity create(JsonNode payload) {
        requireText(payload, "orderId");
        requirePositiveNumber(payload, "amount");

        var currency = requireText(payload, "currency");
        if (currency.length() != 3) {
            throw new IllegalArgumentException("payload field 'currency' must be a 3-letter code");
        }

        var saga = new SagaEntity(SAGA_NAME, payload.toString());
        saga.addStep(AUTHORIZE_FUNDS, VOID_AUTHORIZATION);
        saga.addStep(CAPTURE_PAYMENT, REFUND);
        saga.addStep(POST_TO_LEDGER, REVERSE_LEDGER_ENTRY);

        return saga;
    }
}
