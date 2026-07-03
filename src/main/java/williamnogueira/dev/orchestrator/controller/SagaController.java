package williamnogueira.dev.orchestrator.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/sagas")
public class SagaController {

    @PostMapping
    public ResponseEntity<Void> startSaga() {
        // TODO: build the payment SagaEntity (authorize -> capture -> ledger) from the request,
        //  persist it and dispatch the first step; return 202 with the saga id
        throw new UnsupportedOperationException("not implemented yet");
    }

    @GetMapping("/{id}")
    public ResponseEntity<Void> getSaga(@PathVariable UUID id) {
        // TODO: load the saga with its steps and map to a response dto
        throw new UnsupportedOperationException("not implemented yet");
    }

    @PostMapping("/{id}/retry")
    public ResponseEntity<Void> retrySaga(@PathVariable UUID id) {
        // TODO: manually re-dispatch the stuck/failed step of a non-terminal saga
        throw new UnsupportedOperationException("not implemented yet");
    }
}
