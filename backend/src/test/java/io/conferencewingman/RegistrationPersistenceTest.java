package io.conferencewingman;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.http.*;

import static org.assertj.core.api.Assertions.assertThat;

class RegistrationPersistenceTest {
    private ServletWebServerApplicationContext start() {
        return (ServletWebServerApplicationContext) new SpringApplicationBuilder(WingmanApplication.class)
            .run("--spring.datasource.url=jdbc:postgresql://localhost:5432/wingman_test", "--server.port=0");
    }

    @Test void registrationAndOrganizerCountSurviveApplicationRestart() throws Exception {
        var http = new TestRestTemplate();
        var json = new ObjectMapper();
        String conference;
        String organizer;
        String registration;
        UUID key = UUID.randomUUID();
        var attendee = RegistrationIntegrationTest.attendee(UUID.randomUUID()+"@example.test");
        HttpHeaders headers = new HttpHeaders(); headers.set("Idempotency-Key", key.toString());
        try (var app = start()) {
            String base = "http://localhost:" + app.getWebServer().getPort() + "/api/v1";
            var created = http.postForEntity(base+"/conferences", ConferenceApiIntegrationTest.conference(), String.class);
            assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            var body = json.readTree(created.getBody());
            conference = body.at("/conference/id").asText(); organizer = body.path("organizerToken").asText();
            var response = http.postForEntity(base+"/conferences/"+conference+"/registrations", new HttpEntity<>(attendee, headers), String.class);
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            registration = json.readTree(response.getBody()).path("registrationId").asText();
        }
        try (var app = start()) {
            String base = "http://localhost:" + app.getWebServer().getPort() + "/api/v1";
            assertThat(http.getForEntity(base+"/conferences/"+conference, String.class).getStatusCode()).isEqualTo(HttpStatus.OK);
            HttpHeaders management = new HttpHeaders(); management.setBearerAuth(organizer);
            var count = http.exchange(base+"/conferences/"+conference+"/management", HttpMethod.GET, new HttpEntity<>(management), String.class);
            assertThat(count.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(json.readTree(count.getBody()).path("registeredAttendeeCount").asInt()).isEqualTo(1);
            var replay = http.postForEntity(base+"/conferences/"+conference+"/registrations", new HttpEntity<>(attendee, headers), String.class);
            assertThat(replay.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(json.readTree(replay.getBody()).path("registrationId").asText()).isEqualTo(registration);
        }
    }
}
