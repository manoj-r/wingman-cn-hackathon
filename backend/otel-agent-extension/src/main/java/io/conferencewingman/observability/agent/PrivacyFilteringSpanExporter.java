package io.conferencewingman.observability.agent;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.data.EventData;
import io.opentelemetry.sdk.trace.data.LinkData;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.data.StatusData;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

final class PrivacyFilteringSpanExporter implements SpanExporter {
    private static final Set<String> SAFE_ROUTES = Set.of(
        "/healthz", "/readyz", "/api/v1/version", "/api/v1/conferences",
        "/api/v1/conferences/{id}", "/api/v1/conferences/{id}/management",
        "/api/v1/conferences/{id}/registrations");
    private static final Set<String> SAFE_METHODS = Set.of(
        "GET", "POST", "PUT", "PATCH", "DELETE", "HEAD", "OPTIONS");
    private static final Set<String> SAFE_RESOURCE_KEYS = Set.of(
        "service.name", "service.version", "deployment.environment.name",
        "telemetry.sdk.name", "telemetry.sdk.language", "telemetry.sdk.version",
        "telemetry.distro.name", "telemetry.distro.version");

    private final SpanExporter delegate;

    PrivacyFilteringSpanExporter(SpanExporter delegate) {
        this.delegate = delegate;
    }

    @Override
    public CompletableResultCode export(Collection<SpanData> spans) {
        return delegate.export(spans.stream().map(SanitizedSpanData::new).collect(Collectors.toList()));
    }

    @Override public CompletableResultCode flush() { return delegate.flush(); }
    @Override public CompletableResultCode shutdown() { return delegate.shutdown(); }

    private static Attributes safeSpanAttributes(SpanData span) {
        Attributes source = span.getAttributes();
        AttributesBuilder safe = Attributes.builder();
        String method = source.get(AttributeKey.stringKey("http.request.method"));
        if (isAllowed(SAFE_METHODS, method)) safe.put("http.request.method", method);

        Long status = source.get(AttributeKey.longKey("http.response.status_code"));
        if (status != null && status >= 100 && status <= 599) {
            safe.put("http.response.status_code", status);
        }
        String route = source.get(AttributeKey.stringKey("http.route"));
        if (isAllowed(SAFE_ROUTES, route)) safe.put("http.route", route);

        String database = source.get(AttributeKey.stringKey("db.system"));
        if ("postgresql".equals(database)) safe.put("db.system", database);
        database = source.get(AttributeKey.stringKey("db.system.name"));
        if ("postgresql".equals(database)) safe.put("db.system.name", database);
        String operation = source.get(AttributeKey.stringKey("db.operation.name"));
        if (isAllowed(Set.of("SELECT", "INSERT", "UPDATE", "DELETE", "CALL", "BEGIN", "COMMIT", "ROLLBACK"),
                operation)) {
            safe.put("db.operation.name", operation);
        }

        if ("wingman.registration".equals(span.getName())) {
            String action = source.get(AttributeKey.stringKey("wingman.operation"));
            if ("register".equals(action)) safe.put("wingman.operation", action);
            String outcome = source.get(AttributeKey.stringKey("wingman.outcome"));
            if (isAllowed(Set.of("created", "replayed", "failure"), outcome)) {
                safe.put("wingman.outcome", outcome);
            }
        }
        return safe.build();
    }

    private static boolean isAllowed(Set<String> allowlist, String value) {
        return value != null && allowlist.contains(value);
    }

    private static Attributes safeResourceAttributes(Resource resource) {
        AttributesBuilder safe = Attributes.builder();
        for (String key : SAFE_RESOURCE_KEYS) {
            String value = resource.getAttribute(AttributeKey.stringKey(key));
            if (value != null) safe.put(key, value);
        }
        return safe.build();
    }

    private static final class SanitizedSpanData implements SpanData {
        private final SpanData source;
        private final Attributes attributes;
        private final Resource resource;
        private final List<EventData> events;
        private final List<LinkData> links;
        private final String name;

        SanitizedSpanData(SpanData source) {
            this.source = source;
            this.attributes = safeSpanAttributes(source);
            this.resource = Resource.create(safeResourceAttributes(source.getResource()),
                source.getResource().getSchemaUrl());
            this.events = source.getEvents().stream().map(SanitizedEventData::new).collect(Collectors.toList());
            this.links = source.getLinks().stream().map(SanitizedLinkData::new).collect(Collectors.toList());
            this.name = sanitizedName(source);
        }

        private static String sanitizedName(SpanData span) {
            if ("wingman.registration".equals(span.getName())) return "wingman.registration";
            if (span.getKind() == SpanKind.SERVER) return "HTTP SERVER";
            if (span.getKind() == SpanKind.CLIENT) return "HTTP CLIENT";
            if (span.getAttributes().get(AttributeKey.stringKey("db.system")) != null
                    || span.getAttributes().get(AttributeKey.stringKey("db.system.name")) != null) {
                return "DATABASE";
            }
            return "OPERATION";
        }

        @Override public String getName() { return name; }
        @Override public io.opentelemetry.api.trace.SpanContext getSpanContext() { return source.getSpanContext(); }
        @Override public io.opentelemetry.api.trace.SpanContext getParentSpanContext() { return source.getParentSpanContext(); }
        @Override public SpanKind getKind() { return source.getKind(); }
        @Override public long getStartEpochNanos() { return source.getStartEpochNanos(); }
        @Override public long getEndEpochNanos() { return source.getEndEpochNanos(); }
        @Override public Attributes getAttributes() { return attributes; }
        @Override public List<EventData> getEvents() { return events; }
        @Override public List<LinkData> getLinks() { return links; }
        @Override public StatusData getStatus() { return StatusData.create(source.getStatus().getStatusCode(), null); }
        @Override public Resource getResource() { return resource; }
        @Override public io.opentelemetry.sdk.common.InstrumentationLibraryInfo getInstrumentationLibraryInfo() {
            return source.getInstrumentationLibraryInfo();
        }
        @Override public int getTotalAttributeCount() { return attributes.size(); }
        @Override public int getTotalRecordedEvents() { return events.size(); }
        @Override public int getTotalRecordedLinks() { return links.size(); }
        @Override public boolean hasEnded() { return source.hasEnded(); }
    }

    private static final class SanitizedEventData implements EventData {
        private final EventData source;
        SanitizedEventData(EventData source) { this.source = source; }
        @Override public String getName() { return "exception".equals(source.getName()) ? "exception" : "event"; }
        @Override public long getEpochNanos() { return source.getEpochNanos(); }
        @Override public Attributes getAttributes() { return Attributes.empty(); }
        @Override public int getTotalAttributeCount() { return 0; }
    }

    private static final class SanitizedLinkData implements LinkData {
        private final LinkData source;
        SanitizedLinkData(LinkData source) { this.source = source; }
        @Override public io.opentelemetry.api.trace.SpanContext getSpanContext() { return source.getSpanContext(); }
        @Override public Attributes getAttributes() { return Attributes.empty(); }
        @Override public int getTotalAttributeCount() { return 0; }
    }
}
