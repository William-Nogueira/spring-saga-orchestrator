package williamnogueira.dev.orchestrator.domain.factory;

import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import williamnogueira.dev.orchestrator.domain.model.SagaEntity;

/**
 * Reserving merchant funds, transferring them, and recording settlement.
 */
@Component
public class PayoutSagaFactory implements SagaFactory {

    public static final String SAGA_NAME = "payout";

    public static final String RESERVE_MERCHANT_BALANCE = "reserve-merchant-balance";
    public static final String RELEASE_MERCHANT_BALANCE = "release-merchant-balance";
    public static final String TRANSFER_FUNDS = "transfer-funds";
    public static final String REVERSE_TRANSFER = "reverse-transfer";
    public static final String RECORD_SETTLEMENT = "record-settlement";
    public static final String REVERSE_SETTLEMENT = "reverse-settlement";

    @Override
    public String sagaName() {
        return SAGA_NAME;
    }

    @Override
    public SagaEntity create(JsonNode payload) {
        requireText(payload, "merchantId");
        requirePositiveNumber(payload, "amount");

        var saga = new SagaEntity(SAGA_NAME, payload.toString());
        saga.addStep(RESERVE_MERCHANT_BALANCE, RELEASE_MERCHANT_BALANCE);
        saga.addStep(TRANSFER_FUNDS, REVERSE_TRANSFER);
        saga.addStep(RECORD_SETTLEMENT, REVERSE_SETTLEMENT);

        return saga;
    }
}
