package io.conferencewingman;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = "spring.datasource.url=jdbc:postgresql://localhost:5432/wingman_test")
class CapabilitySecurityTest {
    @Autowired TestRestTemplate http;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate db;

    @Test void privateAttendeeRoutesRejectMissingUnknownExpiredAndRotatedCookies() throws Exception {
        String path = "/api/v1/me/matches";
        assertThat(http.getForEntity(path, String.class).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.COOKIE, "wingman_attendee=unknown");
        assertThat(http.exchange(path, HttpMethod.GET, new HttpEntity<>(headers), String.class).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        var conference = http.postForEntity("/api/v1/conferences", ConferenceApiIntegrationTest.conference(), String.class);
        String id = json.readTree(conference.getBody()).at("/conference/id").asText();
        headers = new HttpHeaders(); headers.set("Idempotency-Key", UUID.randomUUID().toString());
        var request = new HttpEntity<>(RegistrationIntegrationTest.attendee(UUID.randomUUID()+"@example.test"), headers);
        var first = http.postForEntity("/api/v1/conferences/"+id+"/registrations", request, String.class);
        String oldCookie = first.getHeaders().getFirst(HttpHeaders.SET_COOKIE).split(";", 2)[0];
        var replay = http.postForEntity("/api/v1/conferences/"+id+"/registrations", request, String.class);
        headers = new HttpHeaders(); headers.set(HttpHeaders.COOKIE, oldCookie);
        assertThat(http.exchange(path, HttpMethod.GET, new HttpEntity<>(headers), String.class).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        headers.set(HttpHeaders.COOKIE, replay.getHeaders().getFirst(HttpHeaders.SET_COOKIE).split(";", 2)[0]);
        // Matching belongs to M3/M4: an authenticated request reaches routing, not a fabricated result.
        assertThat(http.exchange(path, HttpMethod.GET, new HttpEntity<>(headers), String.class).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        UUID registration = UUID.fromString(json.readTree(first.getBody()).path("registrationId").asText());
        db.update("update registration set session_expires_at=now()-interval '1 second' where id=?", registration);
        assertThat(http.exchange(path, HttpMethod.GET, new HttpEntity<>(headers), String.class).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test void storesOnlyCapabilityDigestsAndRejectsWrongOrganizerToken() throws Exception {
        var created = http.postForEntity("/api/v1/conferences", ConferenceApiIntegrationTest.conference(), String.class);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        var body = json.readTree(created.getBody());
        UUID id = UUID.fromString(body.at("/conference/id").asText());
        String token = body.path("organizerToken").asText();
        byte[] stored = db.queryForObject("select organizer_token_hash from conference where id=?", byte[].class, id);
        assertThat(stored).isEqualTo(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
        HttpHeaders headers = new HttpHeaders(); headers.setBearerAuth("wrong-private-token");
        assertThat(http.exchange("/api/v1/conferences/"+id+"/management", HttpMethod.GET, new HttpEntity<>(headers), String.class).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        headers = new HttpHeaders(); headers.set("Idempotency-Key", UUID.randomUUID().toString());
        var registration = http.postForEntity("/api/v1/conferences/"+id+"/registrations", new HttpEntity<>(RegistrationIntegrationTest.attendee(UUID.randomUUID()+"@example.test"), headers), String.class);
        assertThat(registration.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String cookie = registration.getHeaders().getFirst(HttpHeaders.SET_COOKIE).split(";",2)[0].split("=",2)[1];
        UUID registrationId = UUID.fromString(json.readTree(registration.getBody()).path("registrationId").asText());
        assertThat(db.queryForObject("select session_token_hash from registration where id=?", byte[].class, registrationId))
            .isEqualTo(MessageDigest.getInstance("SHA-256").digest(cookie.getBytes(StandardCharsets.UTF_8)));
        assertThat(registration.getBody()).doesNotContain("email", "session_token_hash", token, cookie);
    }
}
