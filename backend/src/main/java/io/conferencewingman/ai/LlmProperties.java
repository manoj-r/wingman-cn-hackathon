package io.conferencewingman.ai;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("wingman.llm")
public record LlmProperties(String baseUrl, String apiKey, String model) {
    public boolean configured() {
        return baseUrl != null && !baseUrl.isBlank() && apiKey != null && !apiKey.isBlank()
            && model != null && !model.isBlank();
    }
}
