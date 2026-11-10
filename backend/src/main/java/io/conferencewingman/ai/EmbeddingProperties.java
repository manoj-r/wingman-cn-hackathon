package io.conferencewingman.ai;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("wingman.embedding")
public record EmbeddingProperties(String baseUrl, String apiKey, String model, int dimensions) {
    public boolean configured() {
        return baseUrl != null && !baseUrl.isBlank() && apiKey != null && !apiKey.isBlank()
            && model != null && !model.isBlank() && dimensions > 0;
    }
}
