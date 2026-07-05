package williamnogueira.dev.orchestrator.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;
import williamnogueira.dev.orchestrator.domain.factory.PaymentSagaFactory;
import williamnogueira.dev.orchestrator.domain.factory.SagaFactoryRegistry;
import williamnogueira.dev.orchestrator.domain.model.SagaEntity;
import williamnogueira.dev.orchestrator.domain.model.SagaRepository;
import williamnogueira.dev.orchestrator.domain.service.SagaOrchestrator;
import williamnogueira.dev.orchestrator.infra.exception.UnknownSagaTypeException;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.endsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SagaController.class)
class SagaControllerTest {

    private static final UUID SAGA_ID = UUID.fromString("7e57ab1e-0000-4000-8000-000000000042");
    private static final JsonMapper MAPPER = new JsonMapper();
    private static final PaymentSagaFactory PAYMENT_FACTORY = new PaymentSagaFactory();

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SagaOrchestrator sagaOrchestrator;

    @MockitoBean
    private SagaRepository sagaRepository;

    @MockitoBean
    private SagaFactoryRegistry sagaFactoryRegistry;

    @Test
    @DisplayName("POST /sagas resolves the type and returns 202 with a Location header")
    void startSagaResolvesTypeAndReturns202WithLocation() throws Exception {
        when(sagaFactoryRegistry.create(eq("payment"), any())).thenReturn(paymentSaga());
        when(sagaOrchestrator.start(any())).thenAnswer(invocation -> {
            SagaEntity saga = invocation.getArgument(0);
            ReflectionTestUtils.setField(saga, "id", SAGA_ID);
            return saga;
        });

        mockMvc.perform(post("/sagas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"type":"payment","payload":{"orderId":"42","amount":100.50,"currency":"USD"}}
                                """))
                .andExpect(status().isAccepted())
                .andExpect(header().string("Location", endsWith("/sagas/" + SAGA_ID)))
                .andExpect(jsonPath("$.id").value(SAGA_ID.toString()))
                .andExpect(jsonPath("$.name").value(PaymentSagaFactory.SAGA_NAME))
                .andExpect(jsonPath("$.status").value("STARTED"))
                .andExpect(jsonPath("$.steps.length()").value(3))
                .andExpect(jsonPath("$.steps[0].name").value(PaymentSagaFactory.AUTHORIZE_FUNDS))
                .andExpect(jsonPath("$.steps[2].compensation").value(PaymentSagaFactory.REVERSE_LEDGER_ENTRY));
    }

    @Test
    @DisplayName("POST /sagas rejects a request without type and payload")
    void startSagaRejectsMissingTypeAndPayload() throws Exception {
        mockMvc.perform(post("/sagas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.type").exists())
                .andExpect(jsonPath("$.errors.payload").exists());
    }

    @Test
    @DisplayName("POST /sagas rejects an unknown saga type, listing the known ones")
    void startSagaRejectsUnknownType() throws Exception {
        when(sagaFactoryRegistry.create(eq("credit"), any()))
                .thenThrow(new UnknownSagaTypeException("credit", Set.of("payment", "refund", "payout")));

        mockMvc.perform(post("/sagas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"credit\",\"payload\":{}}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString("unknown saga type 'credit'")));
    }

    @Test
    @DisplayName("POST /sagas rejects a payload the factory refuses")
    void startSagaRejectsInvalidPayload() throws Exception {
        when(sagaFactoryRegistry.create(eq("payment"), any()))
                .thenThrow(new IllegalArgumentException("payload field 'orderId' is required"));

        mockMvc.perform(post("/sagas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"payment\",\"payload\":{}}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("payload field 'orderId' is required"));
    }

    @Test
    @DisplayName("GET /sagas/{id} returns the saga with its steps")
    void getSagaReturnsSagaWithSteps() throws Exception {
        var saga = paymentSaga();
        ReflectionTestUtils.setField(saga, "id", SAGA_ID);
        when(sagaRepository.findWithStepsById(SAGA_ID)).thenReturn(Optional.of(saga));

        mockMvc.perform(get("/sagas/{id}", SAGA_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(SAGA_ID.toString()))
                .andExpect(jsonPath("$.steps.length()").value(3));
    }

    @Test
    @DisplayName("POST /sagas/{id}/retry resumes a failed saga with 202")
    void retrySagaReturns202() throws Exception {
        var saga = paymentSaga();
        ReflectionTestUtils.setField(saga, "id", SAGA_ID);
        when(sagaOrchestrator.retry(SAGA_ID)).thenReturn(saga);

        mockMvc.perform(post("/sagas/{id}/retry", SAGA_ID))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.id").value(SAGA_ID.toString()));
    }

    @Test
    @DisplayName("POST /sagas/{id}/retry returns 409 when the saga is not FAILED")
    void retrySagaReturns409WhenNotFailed() throws Exception {
        when(sagaOrchestrator.retry(SAGA_ID))
                .thenThrow(new IllegalStateException("only FAILED sagas can be retried"));

        mockMvc.perform(post("/sagas/{id}/retry", SAGA_ID))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("only FAILED sagas can be retried"));
    }

    @Test
    @DisplayName("POST /sagas/{id}/compensate rolls back an in-flight saga with 202")
    void forceCompensateReturns202() throws Exception {
        var saga = paymentSaga();
        ReflectionTestUtils.setField(saga, "id", SAGA_ID);
        when(sagaOrchestrator.forceCompensate(SAGA_ID)).thenReturn(saga);

        mockMvc.perform(post("/sagas/{id}/compensate", SAGA_ID))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.id").value(SAGA_ID.toString()));
    }

    @Test
    @DisplayName("GET /sagas/{id} returns 404 for an unknown id")
    void getSagaReturns404WhenMissing() throws Exception {
        when(sagaRepository.findWithStepsById(SAGA_ID)).thenReturn(Optional.empty());

        mockMvc.perform(get("/sagas/{id}", SAGA_ID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("saga %s not found".formatted(SAGA_ID)));
    }

    private static SagaEntity paymentSaga() {
        return PAYMENT_FACTORY.create(MAPPER.readTree("{\"orderId\":\"42\",\"amount\":100.50,\"currency\":\"USD\"}"));
    }
}
