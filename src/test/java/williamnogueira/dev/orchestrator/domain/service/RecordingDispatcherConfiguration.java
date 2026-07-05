package williamnogueira.dev.orchestrator.domain.service;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

@TestConfiguration(proxyBeanMethods = false)
public class RecordingDispatcherConfiguration {

    @Bean
    @Primary
    RecordingCommandDispatcher recordingCommandDispatcher() {
        return new RecordingCommandDispatcher();
    }
}
