package io.conferencewingman;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
    "spring.datasource.url=jdbc:postgresql://localhost:5432/wingman_test",
    "wingman.rate-limit.conferences-per-minute=2",
    "wingman.rate-limit.registrations-per-minute=2"
})
class RequestRateLimitTest {
    @Autowired TestRestTemplate http;

    @Test void repeatedPublicConferenceCreationIsBounded() throws Exception {
        var first = http.postForEntity("/api/v1/conferences", ConferenceApiIntegrationTest.conference(), String.class);
        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(http.postForEntity("/api/v1/conferences", ConferenceApiIntegrationTest.conference(), String.class).getStatusCode()).isEqualTo(HttpStatus.CREATED);
        var limited = http.postForEntity("/api/v1/conferences", ConferenceApiIntegrationTest.conference(), String.class);
        assertThat(limited.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(limited.getHeaders().getFirst(HttpHeaders.RETRY_AFTER)).isNotBlank();
        assertThat(limited.getBody()).contains("RATE_LIMITED");
        String conference = new com.fasterxml.jackson.databind.ObjectMapper().readTree(first.getBody()).at("/conference/id").asText();
        String path = "/api/v1/conferences/"+conference+"/registrations";
        for (int i = 0; i < 2; i++) {
            HttpHeaders headers = new HttpHeaders(); headers.set("Idempotency-Key", java.util.UUID.randomUUID().toString());
            assertThat(http.postForEntity(path, new HttpEntity<>(RegistrationIntegrationTest.attendee(java.util.UUID.randomUUID()+"@example.test"), headers), String.class)
                .getStatusCode()).isEqualTo(HttpStatus.CREATED);
        }
        var registrationLimited = http.postForEntity(path, RegistrationIntegrationTest.attendee("unused@example.test"), String.class);
        assertThat(registrationLimited.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(registrationLimited.getHeaders().getFirst(HttpHeaders.RETRY_AFTER)).isNotBlank();
    }
}
