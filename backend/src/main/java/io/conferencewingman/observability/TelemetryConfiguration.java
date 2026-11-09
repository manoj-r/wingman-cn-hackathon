package io.conferencewingman.observability;

import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporter;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;
import java.time.Duration;
import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;
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
            Map<String, String> parsedHeaders = parseHeaders(headers);
            provider.addSpanProcessor(BatchSpanProcessor.builder(
                    OtlpHttpSpanExporter.builder()
                        .setEndpoint(endpoint)
                        .setHeaders(() -> parsedHeaders)
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
        Map<String, String> headers = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (String rawEntry : value.split(",")) {
            String entry = rawEntry.trim();
            if (entry.isEmpty()) continue;
            int separator = entry.indexOf('=');
            if (separator <= 0) throw new IllegalArgumentException("Malformed OTLP header configuration");
            String key = entry.substring(0, separator).trim();
            String headerValue = entry.substring(separator + 1).trim();
            if (key.isEmpty()) throw new IllegalArgumentException("Malformed OTLP header configuration");
            if (headers.putIfAbsent(key, headerValue) != null) {
                throw new IllegalArgumentException("Duplicate OTLP header configuration");
            }
        }
        return Collections.unmodifiableMap(headers);
    }
}
