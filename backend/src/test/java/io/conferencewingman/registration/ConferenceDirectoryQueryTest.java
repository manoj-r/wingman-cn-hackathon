package io.conferencewingman.registration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = "spring.datasource.url=jdbc:postgresql://localhost:5432/wingman_test")
class ConferenceDirectoryQueryTest {
    @Autowired TestRestTemplate http;
    @Autowired ObjectMapper json;
    @Autowired ConferenceRepository conferences;

    @Test void directoryExcludesPastEventsAndReturnsAtMostOneHundredInStartOrder() throws Exception {
        Instant now = Instant.now();
        conferences.saveAndFlush(conference("Expired conference", now.minusSeconds(7200), now.minusSeconds(1)));
        List<Conference> upcoming = new ArrayList<>();
        for (int i = 0; i < 105; i++) {
            Instant start = now.plusSeconds(3600L * (i + 1));
            upcoming.add(conference("Upcoming " + i, start, start.plusSeconds(3600)));
        }
        conferences.saveAllAndFlush(upcoming);

        var response = http.getForEntity("/api/v1/conferences", String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode body = json.readTree(response.getBody());
        assertThat(body.size()).isEqualTo(100);
        assertThat(body.toString()).doesNotContain("Expired conference");
        for (int i = 1; i < body.size(); i++) {
            assertThat(Instant.parse(body.get(i).path("startDate").asText()))
                .isAfterOrEqualTo(Instant.parse(body.get(i - 1).path("startDate").asText()));
        }
    }

    private static Conference conference(String name, Instant start, Instant end) {
        Conference conference = new Conference();
        conference.id = UUID.randomUUID();
        conference.name = name;
        conference.description = "Directory query test";
        conference.location = "Test location";
        conference.startDate = start;
        conference.endDate = end;
        conference.organizerTokenHash = UUID.randomUUID().toString().getBytes(StandardCharsets.UTF_8);
        conference.createdAt = Instant.now();
        return conference;
    }
}
