package williamnogueira.dev.orchestrator.domain.factory;

import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import williamnogueira.dev.orchestrator.domain.model.SagaEntity;

/**
 * Refunding a captured payment.
 */
@Component
public class RefundSagaFactory implements SagaFactory {

    public static final String SAGA_NAME = "refund";

    public static final String ISSUE_REFUND = "issue-refund";
    public static final String RECLAIM_REFUND = "reclaim-refund";
    public static final String REVERSE_LEDGER_ENTRY = "reverse-ledger-entry";
    public static final String REPOST_LEDGER_ENTRY = "repost-ledger-entry";

    @Override
    public String sagaName() {
        return SAGA_NAME;
    }

    @Override
    public SagaEntity create(JsonNode payload) {
        requireText(payload, "paymentId");

        var saga = new SagaEntity(SAGA_NAME, payload.toString());
        saga.addStep(ISSUE_REFUND, RECLAIM_REFUND);
        saga.addStep(REVERSE_LEDGER_ENTRY, REPOST_LEDGER_ENTRY);
        return saga;
    }
}
