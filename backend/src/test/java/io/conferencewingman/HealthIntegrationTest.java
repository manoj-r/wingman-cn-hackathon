package io.conferencewingman;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
    "spring.datasource.url=jdbc:postgresql://localhost:5432/wingman_test",
    "wingman.version=test-revision"
})
class HealthIntegrationTest {
    @Autowired TestRestTemplate http;

    @Test void livenessPreservesStarterContract() {
        var response = http.getForEntity("/healthz", String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo("ok");
    }

    @Test void readinessReportsApplicationReadiness() {
        assertThat(http.getForEntity("/readyz", String.class).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test void versionIdentifiesRunningBackendRevision() {
        var response = http.getForEntity("/api/v1/version", String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).contains("test-revision", "conference-wingman-backend");
    }
}
