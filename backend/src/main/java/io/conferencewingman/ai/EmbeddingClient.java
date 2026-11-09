package io.conferencewingman.ai;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class EmbeddingClient {
    private final RestClient http;
    private final EmbeddingProperties properties;

    public EmbeddingClient(RestClient.Builder builder, EmbeddingProperties properties) {
        this.http = builder.build(); this.properties = properties;
    }

    public float[] embed(String text) {
        if (!properties.configured()) throw new EmbeddingException("Embedding provider is not configured");
        try {
            JsonNode response = http.post().uri(properties.baseUrl().replaceAll("/$", "") + "/embeddings")
                .header("Authorization", "Bearer " + properties.apiKey())
                .body(Map.of("model", properties.model(), "input", text)).retrieve().body(JsonNode.class);
            JsonNode values = response == null ? null : response.path("data").path(0).path("embedding");
            if (values == null || !values.isArray() || values.size() != properties.dimensions())
                throw new EmbeddingException("Embedding provider returned an invalid vector");
            float[] vector = new float[values.size()]; double norm = 0;
            for (int i=0; i<vector.length; i++) {
                if (!values.get(i).isNumber()) throw new EmbeddingException("Embedding provider returned an invalid vector");
                vector[i]=values.get(i).floatValue();
                if (!Float.isFinite(vector[i])) throw new EmbeddingException("Embedding provider returned an invalid vector");
                norm += vector[i] * (double) vector[i];
            }
            if (norm == 0) throw new EmbeddingException("Embedding provider returned an invalid vector");
            return vector;
        } catch (EmbeddingException error) { throw error; }
        catch (RuntimeException error) { throw new EmbeddingException("Embedding provider is unavailable"); }
    }

    public boolean configured() { return properties.configured(); }

    public static class EmbeddingException extends RuntimeException {
        public EmbeddingException(String message) { super(message); }
    }
}
