package io.conferencewingman;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = "spring.datasource.url=jdbc:postgresql://localhost:5432/wingman_test")
class ConferenceApiIntegrationTest {
    @Autowired TestRestTemplate http;
    @Autowired ObjectMapper json;

    static Map<String, String> conference() {
        return Map.of("name", "Fictional Builders", "description", "Meet fellow builders", "location", "Denver",
            "startDate", "2026-11-09T16:00:00Z", "endDate", "2026-11-10T01:00:00Z");
    }

    @Test void organizerCreatesConferenceAndPrivatelyReadsCount() throws Exception {
        var response = http.postForEntity("/api/v1/conferences", conference(), String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getHeaders().getCacheControl()).contains("no-store");
        JsonNode body = json.readTree(response.getBody());
        String id = body.at("/conference/id").asText();
        String token = body.path("organizerToken").asText();
        assertThat(token.length()).isGreaterThanOrEqualTo(43);
        assertThat(body.path("registrationUrl").asText()).endsWith("/conferences/" + id + "/register").doesNotContain(token);
        String publicBody = http.getForObject("/api/v1/conferences/" + id, String.class);
        assertThat(publicBody).contains("Fictional Builders").doesNotContain(token, "organizerToken", "email");
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        var management = http.exchange("/api/v1/conferences/" + id + "/management", HttpMethod.GET, new HttpEntity<>(headers), String.class);
        assertThat(management.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(json.readTree(management.getBody()).path("registeredAttendeeCount").asInt()).isZero();
        assertThat(http.getForEntity("/api/v1/conferences/" + id + "/management", String.class).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test void endBeforeStartIsRejected() {
        var input = new java.util.HashMap<>(conference());
        input.put("endDate", "2026-11-08T00:00:00Z");
        assertThat(http.postForEntity("/api/v1/conferences", input, String.class).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test void trimsBeforeCheckingConferenceFieldLengths() throws Exception {
        var input = new java.util.HashMap<>(conference());
        input.put("name", "  " + "x".repeat(120) + "  ");
        var response = http.postForEntity("/api/v1/conferences", input, String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(json.readTree(response.getBody()).at("/conference/name").asText()).isEqualTo("x".repeat(120));
    }
}
