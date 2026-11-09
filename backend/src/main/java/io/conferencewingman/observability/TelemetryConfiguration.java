package io.conferencewingman.observability;

import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporter;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;
import java.time.Duration;
import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class TelemetryConfiguration {
    @Bean(destroyMethod = "close")
    OpenTelemetrySdk openTelemetrySdk(
            @Value("${OTEL_TRACES_EXPORTER:none}") String exporter,
            @Value("${OTEL_EXPORTER_OTLP_ENDPOINT:}") String endpoint,
            @Value("${OTEL_EXPORTER_OTLP_HEADERS:}") String headers,
            @Value("${OTEL_SERVICE_NAME:conference-wingman-backend}") String serviceName) {
        var provider = SdkTracerProvider.builder().setResource(Resource.getDefault().toBuilder()
            .put("service.name", serviceName).build());
        if ("otlp".equalsIgnoreCase(exporter) && !endpoint.isBlank()) {
            provider.addSpanProcessor(BatchSpanProcessor.builder(
                    OtlpHttpSpanExporter.builder()
                        .setEndpoint(endpoint)
                        .setHeaders(() -> parseHeaders(headers))
                        .setTimeout(Duration.ofSeconds(5))
                        .build())
                .setExporterTimeout(Duration.ofSeconds(5))
                .build());
        }
        return OpenTelemetrySdk.builder().setTracerProvider(provider.build()).build();
    }

    @Bean
    Tracer registrationTracer(OpenTelemetrySdk sdk) {
        return sdk.getTracer("io.conferencewingman.registration");
    }

    static Map<String, String> parseHeaders(String value) {
        if (value.isBlank()) return Map.of();
        return Arrays.stream(value.split(","))
            .map(String::trim)
            .filter(entry -> !entry.isEmpty())
            .map(entry -> {
                int separator = entry.indexOf('=');
                if (separator <= 0) throw new IllegalArgumentException("Malformed OTLP header configuration");
                return Map.entry(entry.substring(0, separator).trim(), entry.substring(separator + 1).trim());
            })
            .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue));
    }
}
