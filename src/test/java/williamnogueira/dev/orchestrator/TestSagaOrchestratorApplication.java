package williamnogueira.dev.orchestrator;

import org.springframework.boot.SpringApplication;

public class TestSagaOrchestratorApplication {
    static void main(String[] args) {
        SpringApplication.from(SagaOrchestratorApplication::main).with(TestcontainersConfiguration.class).run(args);
    }
}
