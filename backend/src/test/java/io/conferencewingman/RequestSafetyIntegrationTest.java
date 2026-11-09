package io.conferencewingman;

import java.util.HashMap;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = "spring.datasource.url=jdbc:postgresql://localhost:5432/wingman_test")
class RequestSafetyIntegrationTest {
    @Autowired TestRestTemplate http;

    @Test void oversizedJsonIsRejectedBeforeFieldValidation() {
        var input = new HashMap<>(ConferenceApiIntegrationTest.conference());
        input.put("description", "x".repeat(17000));
        var response = http.postForEntity("/api/v1/conferences", input, String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).contains("BODY_TOO_LARGE");
    }

    @Test void browserMutationFromAnotherOriginIsRejected() {
        HttpHeaders headers = new HttpHeaders();
        headers.setOrigin("https://unrelated.example.test");
        var response = http.postForEntity("/api/v1/conferences", new HttpEntity<>(ConferenceApiIntegrationTest.conference(), headers), String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody()).contains("ORIGIN_NOT_ALLOWED");
    }

    @Test void configuredOriginIsAllowedAndNullOriginIsDenied() {
        HttpHeaders headers = new HttpHeaders();
        headers.setOrigin("http://localhost:3000");
        assertThat(http.postForEntity("/api/v1/conferences", new HttpEntity<>(ConferenceApiIntegrationTest.conference(), headers), String.class)
            .getStatusCode()).isEqualTo(HttpStatus.CREATED);
        headers.setOrigin("null");
        assertThat(http.postForEntity("/api/v1/conferences", new HttpEntity<>(ConferenceApiIntegrationTest.conference(), headers), String.class)
            .getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }
}
