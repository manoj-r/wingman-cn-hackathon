package io.conferencewingman.observability;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Tracer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class TelemetryConfiguration {
    @Bean
    Tracer registrationTracer() {
        return GlobalOpenTelemetry.get().getTracer("io.conferencewingman.registration");
    }
}
