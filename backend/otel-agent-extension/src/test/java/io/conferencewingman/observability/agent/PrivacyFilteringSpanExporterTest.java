package io.conferencewingman.observability.agent;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import org.junit.jupiter.api.Test;

class PrivacyFilteringSpanExporterTest {
    @Test
    void stripsSensitiveHttpUrlHeadersBodyExceptionAndResourceDataBeforeExport() {
        var memory = InMemorySpanExporter.create();
        var resource = Resource.create(Attributes.builder()
            .put("service.name", "wingman-test")
            .put("process.command_args", "--token=private-token")
            .build());
        try (var provider = SdkTracerProvider.builder()
                .setResource(resource)
                .addSpanProcessor(SimpleSpanProcessor.create(new PrivacyFilteringSpanExporter(memory)))
                .build()) {
            var span = provider.get("test").spanBuilder("POST /api/v1/conferences/attendee-123/registrations")
                .setSpanKind(SpanKind.SERVER)
                .startSpan();
            span.setAttribute("http.request.method", "POST");
            span.setAttribute("http.response.status_code", 201L);
            span.setAttribute("http.route", "/api/v1/conferences/{id}/registrations");
            span.setAttribute("url.path", "/api/v1/conferences/attendee-123/registrations");
            span.setAttribute("url.query", "email=private@example.test&token=private-token");
            span.setAttribute("url.full", "https://example.test/private/attendee-123");
            span.setAttribute("http.request.header.authorization", "Bearer private-token");
            span.setAttribute("attendee.name", "Private Name");
            span.setAttribute("gen_ai.prompt", "private prompt");
            span.setAttribute("vector.raw", "private-vector");
            span.recordException(new IllegalArgumentException("private@example.test private-token"));
            span.end();

            SpanData exported = memory.getFinishedSpanItems().getFirst();
            assertThat(exported.getName()).isEqualTo("HTTP SERVER");
            assertThat(exported.getAttributes().asMap())
                .containsOnlyKeys(
                    AttributeKey.stringKey("http.request.method"),
                    AttributeKey.longKey("http.response.status_code"),
                    AttributeKey.stringKey("http.route"))
                .containsEntry(AttributeKey.stringKey("http.route"), "/api/v1/conferences/{id}/registrations");
            assertThat(exported.getEvents()).hasSize(1);
            assertThat(exported.getEvents().getFirst().getAttributes().asMap()).isEmpty();
            assertThat(exported.getResource().getAttributes().asMap())
                .containsOnlyKeys(AttributeKey.stringKey("service.name"));
            assertThat(exported.getStatus().getDescription()).isEmpty();
            assertThat(exported.toString()).doesNotContain(
                "attendee-123", "private@example.test", "private-token", "Private Name",
                "private prompt", "private-vector");
        }
    }

    @Test
    void preservesOnlyTheFixedRegistrationDomainSpanFields() {
        var memory = InMemorySpanExporter.create();
        try (var provider = SdkTracerProvider.builder()
                .addSpanProcessor(SimpleSpanProcessor.create(new PrivacyFilteringSpanExporter(memory)))
                .build()) {
            var span = provider.get("test").spanBuilder("wingman.registration").startSpan();
            span.setAttribute("wingman.operation", "register");
            span.setAttribute("wingman.outcome", "created");
            span.setAttribute("email", "private@example.test");
            span.end();

            SpanData exported = memory.getFinishedSpanItems().getFirst();
            assertThat(exported.getName()).isEqualTo("wingman.registration");
            assertThat(exported.getAttributes().asMap())
                .containsOnlyKeys(AttributeKey.stringKey("wingman.operation"),
                    AttributeKey.stringKey("wingman.outcome"));
        }
    }
}
