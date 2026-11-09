package io.conferencewingman;

import io.conferencewingman.ai.EmbeddingClient;
import io.conferencewingman.ai.EmbeddingProperties;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class EmbeddingClientTest {
    @Test void callsConfiguredRealProviderAndValidatesVector() {
        var properties = new EmbeddingProperties("https://provider.example/v1", "secret", "embed-model", 3);
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        var client = new EmbeddingClient(builder, properties);
        server.expect(requestTo("https://provider.example/v1/embeddings"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(header("Authorization", "Bearer secret"))
            .andExpect(content().json("{\"model\":\"embed-model\",\"input\":\"profile text\"}"))
            .andRespond(withSuccess("{\"data\":[{\"embedding\":[0.1,0.2,0.3]}]}", org.springframework.http.MediaType.APPLICATION_JSON));

        assertThat(client.embed("profile text")).containsExactly(0.1f, 0.2f, 0.3f);
        server.verify();
    }

    @Test void rejectsMissingOrMalformedProviderVectorsWithoutReturningProviderBody() {
        var properties = new EmbeddingProperties("https://provider.example/v1", "secret", "embed-model", 2);
        var builder = RestClient.builder();
        var server = MockRestServiceServer.bindTo(builder).build();
        var client = new EmbeddingClient(builder, properties);
        server.expect(requestTo("https://provider.example/v1/embeddings"))
            .andRespond(withSuccess("{\"data\":[{\"embedding\":[0.0,\"bad\"]}]}", org.springframework.http.MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.embed("profile text"))
            .isInstanceOf(EmbeddingClient.EmbeddingException.class)
            .hasMessageNotContaining("bad");
        server.verify();
    }
}
