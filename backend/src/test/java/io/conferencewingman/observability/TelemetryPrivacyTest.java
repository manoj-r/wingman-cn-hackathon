package io.conferencewingman.observability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import io.opentelemetry.sdk.common.CompletableResultCode;
import java.util.Collection;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class TelemetryPrivacyTest {
    @Test
    void registrationSpanContainsOnlyBoundedOperationAndOutcome() {
        var exporter = InMemorySpanExporter.create();
        try (var provider = SdkTracerProvider.builder()
                .addSpanProcessor(SimpleSpanProcessor.create(exporter)).build()) {
            var telemetry = new RegistrationTelemetry(provider.get("test"));
            assertThat(telemetry.record(() -> "created", outcome -> outcome)).isEqualTo("created");

            var span = exporter.getFinishedSpanItems().getFirst();
            assertThat(span.getName()).isEqualTo("wingman.registration");
            assertThat(span.getAttributes().asMap())
                .containsOnlyKeys(
                    io.opentelemetry.api.common.AttributeKey.stringKey("wingman.operation"),
                    io.opentelemetry.api.common.AttributeKey.stringKey("wingman.outcome"))
                .containsEntry(io.opentelemetry.api.common.AttributeKey.stringKey("wingman.operation"), "register")
                .containsEntry(io.opentelemetry.api.common.AttributeKey.stringKey("wingman.outcome"), "created");
        }
    }

    @Test
    void telemetryFailureDoesNotEscapeRegistrationOperation() {
        SpanExporter failingExporter = new SpanExporter() {
            @Override public CompletableResultCode export(Collection<SpanData> spans) {
                return CompletableResultCode.ofFailure();
            }
            @Override public CompletableResultCode flush() { return CompletableResultCode.ofSuccess(); }
            @Override public CompletableResultCode shutdown() { return CompletableResultCode.ofSuccess(); }
        };
        try (var provider = SdkTracerProvider.builder().addSpanProcessor(
                BatchSpanProcessor.builder(failingExporter).setScheduleDelay(Duration.ofMillis(1)).build()).build()) {
            var telemetry = new RegistrationTelemetry(provider.get("test"));
            assertThatCode(() -> telemetry.record(() -> "completed", outcome -> outcome))
                .doesNotThrowAnyException();
        }
    }

    @Test
    void exporterDisabledConfigurationStartsWithoutEndpointOrCredentials() {
        try (var sdk = new TelemetryConfiguration().openTelemetrySdk("none", "", "", "test-service")) {
            assertThat(sdk.getTracer("test")).isNotNull();
        }
    }

    @Test
    void malformedPrivateHeadersFailWithoutEchoingTheirValues() {
        var failure = org.assertj.core.api.Assertions.catchThrowable(
            () -> TelemetryConfiguration.parseHeaders("secret-value-without-separator"));
        assertThat(failure).hasMessage("Malformed OTLP header configuration")
            .hasMessageNotContaining("secret-value");
    }
}
