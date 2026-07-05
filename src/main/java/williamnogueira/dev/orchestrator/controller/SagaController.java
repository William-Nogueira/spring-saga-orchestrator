package williamnogueira.dev.orchestrator.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import williamnogueira.dev.orchestrator.controller.dto.SagaResponse;
import williamnogueira.dev.orchestrator.controller.dto.StartSagaRequest;
import williamnogueira.dev.orchestrator.domain.factory.SagaFactoryRegistry;
import williamnogueira.dev.orchestrator.domain.model.SagaRepository;
import williamnogueira.dev.orchestrator.domain.service.SagaOrchestrator;
import williamnogueira.dev.orchestrator.infra.exception.SagaNotFoundException;

import java.util.UUID;

@RestController
@RequestMapping("/sagas")
@RequiredArgsConstructor
@Tag(name = "Saga Management", description = "Start, inspect and retry sagas")
public class SagaController {

    private final SagaOrchestrator sagaOrchestrator;
    private final SagaRepository sagaRepository;
    private final SagaFactoryRegistry sagaFactoryRegistry;

    @Operation(
            summary = "Start a saga",
            description = "Builds the saga of the requested type from its payload and dispatches the first step.",
            responses = {
                    @ApiResponse(responseCode = "202", description = "Saga accepted; processing continues asynchronously"),
                    @ApiResponse(responseCode = "400", description = "Unknown saga type or invalid payload")
            }
    )
    @PostMapping
    public ResponseEntity<SagaResponse> startSaga(@Valid @RequestBody StartSagaRequest request) {
        var saga = sagaOrchestrator.start(sagaFactoryRegistry.create(request.type(), request.payload()));
        var location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(saga.getId())
                .toUri();

        return ResponseEntity.accepted().location(location).body(SagaResponse.from(saga));
    }

    @Operation(
            summary = "Inspect a saga",
            description = "Returns the saga with the live status of every step.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Saga found"),
                    @ApiResponse(responseCode = "404", description = "No saga with this id")
            }
    )
    @GetMapping("/{id}")
    public ResponseEntity<SagaResponse> getSaga(@PathVariable UUID id) {
        var saga = sagaRepository.findWithStepsById(id).orElseThrow(() -> new SagaNotFoundException(id));
        return ResponseEntity.ok(SagaResponse.from(saga));
    }

    @Operation(
            summary = "Retry a failed saga",
            description = "Resumes the compensation of a FAILED saga, granting the failed step one more attempt.",
            responses = {
                    @ApiResponse(responseCode = "202", description = "Compensation resumed"),
                    @ApiResponse(responseCode = "404", description = "No saga with this id"),
                    @ApiResponse(responseCode = "409", description = "Saga is not in a retryable state")
            }
    )
    @PostMapping("/{id}/retry")
    public ResponseEntity<SagaResponse> retrySaga(@PathVariable UUID id) {
        return ResponseEntity.accepted().body(SagaResponse.from(sagaOrchestrator.retry(id)));
    }

    @Operation(
            summary = "Force-compensate a saga",
            description = "Abandons the in-flight step of a STARTED saga and rolls back every completed step.",
            responses = {
                    @ApiResponse(responseCode = "202", description = "Compensation started"),
                    @ApiResponse(responseCode = "404", description = "No saga with this id"),
                    @ApiResponse(responseCode = "409", description = "Saga is not in flight")
            }
    )
    @PostMapping("/{id}/compensate")
    public ResponseEntity<SagaResponse> forceCompensate(@PathVariable UUID id) {
        return ResponseEntity.accepted().body(SagaResponse.from(sagaOrchestrator.forceCompensate(id)));
    }
}
