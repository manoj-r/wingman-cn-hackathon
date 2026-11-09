package io.conferencewingman;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = "spring.datasource.url=jdbc:postgresql://localhost:5432/wingman_test")
@ExtendWith(OutputCaptureExtension.class)
class RegistrationIntegrationTest {
    @Autowired TestRestTemplate http;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate db;

    String conferenceId() throws Exception {
        var response = http.postForEntity("/api/v1/conferences", ConferenceApiIntegrationTest.conference(), String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return json.readTree(response.getBody()).at("/conference/id").asText();
    }

    static Map<String, Object> attendee(String email) {
        return Map.of("name", "Alex Example", "email", email, "jobTitle", "Engineer", "company", "Example Labs",
            "bio", "Building developer platforms", "interests", List.of("Kubernetes", "OpenTelemetry"),
            "networkingGoals", "Meet fellow builders");
    }

    ResponseEntity<String> register(String conference, UUID key, Map<String, Object> body) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Idempotency-Key", key.toString());
        return http.postForEntity("/api/v1/conferences/" + conference + "/registrations", new HttpEntity<>(body, headers), String.class);
    }

    @Test void registrationPersistsAndReplaysWithoutDuplicates(CapturedOutput output) throws Exception {
        String conference = conferenceId(); UUID key = UUID.randomUUID();
        String email = UUID.randomUUID() + "@example.test";
        var first = register(conference, key, attendee(email));
        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        var replay = register(conference, key, attendee(email));
        assertThat(replay.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(json.readTree(replay.getBody()).path("registrationId")).isEqualTo(json.readTree(first.getBody()).path("registrationId"));
        assertThat(first.getHeaders().getFirst(HttpHeaders.SET_COOKIE)).contains("HttpOnly", "SameSite=Lax", "Path=/api/v1");
        assertThat(replay.getHeaders().getFirst(HttpHeaders.SET_COOKIE)).isNotEqualTo(first.getHeaders().getFirst(HttpHeaders.SET_COOKIE));
        assertThat(db.queryForObject("select count(*) from registration where conference_id=?", Integer.class, UUID.fromString(conference))).isEqualTo(1);
        assertThat(register(conference, key, attendee("other@example.test")).getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        var duplicate = register(conference, UUID.randomUUID(), attendee(email));
        assertThat(duplicate.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(duplicate.getBody()).doesNotContain(email, json.readTree(first.getBody()).path("registrationId").asText());
        assertThat(db.queryForObject("select count(*) from attendee where email=?", Integer.class, email)).isEqualTo(1);
        assertThat(output.getAll()).doesNotContain(email);
    }

    @Test void concurrentIdenticalRequestsCreateExactlyOneRegistrationAndAttendee() throws Exception {
        String conference = conferenceId(); UUID key = UUID.randomUUID();
        String email = UUID.randomUUID() + "@example.test";
        var executor = Executors.newFixedThreadPool(2);
        try {
            var barrier = new CyclicBarrier(2);
            Callable<ResponseEntity<String>> request = () -> { barrier.await(5, TimeUnit.SECONDS); return register(conference, key, attendee(email)); };
            var futures = executor.invokeAll(List.of(request, request), 15, TimeUnit.SECONDS);
            var responses = List.of(futures.get(0).get(), futures.get(1).get());
            assertThat(responses.stream().map(r -> r.getStatusCode().value()).sorted().toList()).containsExactly(200, 201);
            assertThat(json.readTree(responses.get(0).getBody()).path("registrationId")).isEqualTo(json.readTree(responses.get(1).getBody()).path("registrationId"));
            assertThat(db.queryForObject("select count(*) from registration where conference_id=?", Integer.class, UUID.fromString(conference))).isEqualTo(1);
            assertThat(db.queryForObject("select count(*) from attendee where email=?", Integer.class, email)).isEqualTo(1);
        } finally { executor.shutdownNow(); }
    }

    @Test void sameEmailCanJoinAnotherConferenceWithoutOverwritingProfile() throws Exception {
        String email = UUID.randomUUID() + "@example.test";
        assertThat(register(conferenceId(), UUID.randomUUID(), attendee(email)).getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(register(conferenceId(), UUID.randomUUID(), attendee(email)).getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(db.queryForObject("select count(*) from attendee where email=?", Integer.class, email)).isEqualTo(2);
    }

    @Test void normalizedWhitespaceAndEmailCaseReplayTheSameSubmission() throws Exception {
        String conference = conferenceId(); UUID key = UUID.randomUUID();
        String email = UUID.randomUUID() + "@example.test";
        assertThat(register(conference, key, attendee(email)).getStatusCode()).isEqualTo(HttpStatus.CREATED);
        var padded = new HashMap<>(attendee(email));
        padded.put("email", "  " + email.toUpperCase(Locale.ROOT) + "  ");
        padded.put("name", "  Alex Example  ");
        padded.put("interests", List.of(" Kubernetes ", " OpenTelemetry "));
        assertThat(register(conference, key, padded).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test void trimsBeforeCheckingAttendeeAndInterestLengths() throws Exception {
        String conference = conferenceId();
        var input = new HashMap<>(attendee(UUID.randomUUID() + "@example.test"));
        input.put("name", "  " + "x".repeat(120) + "  ");
        input.put("interests", List.of("  " + "x".repeat(60) + "  "));
        assertThat(register(conference, UUID.randomUUID(), input).getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }
}
