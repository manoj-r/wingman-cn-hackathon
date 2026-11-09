package io.conferencewingman.observability.agent;

import io.opentelemetry.sdk.autoconfigure.spi.AutoConfigurationCustomizer;
import io.opentelemetry.sdk.autoconfigure.spi.AutoConfigurationCustomizerProvider;

public final class PrivacyExporterCustomizer implements AutoConfigurationCustomizerProvider {
    @Override
    public void customize(AutoConfigurationCustomizer customizer) {
        customizer.addSpanExporterCustomizer(
            (exporter, config) -> new PrivacyFilteringSpanExporter(exporter));
    }
}
