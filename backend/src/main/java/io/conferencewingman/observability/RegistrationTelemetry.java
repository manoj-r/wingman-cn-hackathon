package io.conferencewingman.observability;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import java.util.function.Supplier;
import java.util.function.Function;
import org.springframework.stereotype.Component;

@Component
public class RegistrationTelemetry {
    private final Tracer tracer;

    public RegistrationTelemetry(Tracer tracer) {
        this.tracer = tracer;
    }

    public <T> T record(Supplier<T> operation, Function<T, String> outcome) {
        Span span = tracer.spanBuilder("wingman.registration").startSpan();
        span.setAttribute("wingman.operation", "register");
        try (var scope = span.makeCurrent()) {
            T result = operation.get();
            span.setAttribute("wingman.outcome", outcome.apply(result));
            return result;
        } catch (RuntimeException | Error failure) {
            span.setAttribute("wingman.outcome", "failure");
            span.setStatus(StatusCode.ERROR);
            throw failure;
        } finally {
            span.end();
        }
    }
}
