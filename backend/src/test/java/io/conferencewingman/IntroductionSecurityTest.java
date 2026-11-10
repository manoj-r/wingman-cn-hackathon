package io.conferencewingman;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties="spring.datasource.url=jdbc:postgresql://localhost:5432/wingman_test")
class IntroductionSecurityTest {
    @Autowired TestRestTemplate http;
    @Autowired ObjectMapper json;
    @Autowired org.springframework.jdbc.core.JdbcTemplate db;

    @Test void introductionRequiresAttendeeCapability() {
        var headers=new HttpHeaders(); headers.setContentType(MediaType.APPLICATION_JSON);
        var request=new HttpEntity<>("{\"targetAttendeeId\":\""+UUID.randomUUID()+"\"}",headers);
        var response=http.postForEntity("/api/v1/me/introductions",request,String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody()).contains("UNAUTHORIZED").doesNotContain("email","provider");
    }

    @Test void authenticatedIntroductionCannotAddressUnstoredTarget() throws Exception {
        var created=http.postForEntity("/api/v1/conferences",ConferenceApiIntegrationTest.conference(),String.class);
        String conference=json.readTree(created.getBody()).at("/conference/id").asText();
        var headers=new HttpHeaders(); headers.set("Idempotency-Key",UUID.randomUUID().toString());
        var registered=http.postForEntity("/api/v1/conferences/"+conference+"/registrations",
            new HttpEntity<>(RegistrationIntegrationTest.attendee(UUID.randomUUID()+"@example.test"),headers),String.class);
        headers=new HttpHeaders(); headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set(HttpHeaders.COOKIE,registered.getHeaders().getFirst(HttpHeaders.SET_COOKIE).split(";",2)[0]);
        var response=http.postForEntity("/api/v1/me/introductions",new HttpEntity<>("{\"targetAttendeeId\":\""+UUID.randomUUID()+"\"}",headers),String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).contains("NOT_FOUND").doesNotContain("email","provider");
    }

    @Test void targetRegisteredOnlyInAnotherConferenceIsNotAccessible() throws Exception {
        var first=http.postForEntity("/api/v1/conferences",ConferenceApiIntegrationTest.conference(),String.class);
        var second=http.postForEntity("/api/v1/conferences",ConferenceApiIntegrationTest.conference(),String.class);
        String firstId=json.readTree(first.getBody()).at("/conference/id").asText();
        String secondId=json.readTree(second.getBody()).at("/conference/id").asText();
        HttpHeaders headers=new HttpHeaders(); headers.set("Idempotency-Key",UUID.randomUUID().toString());
        var own=http.postForEntity("/api/v1/conferences/"+firstId+"/registrations",
            new HttpEntity<>(RegistrationIntegrationTest.attendee(UUID.randomUUID()+"@example.test"),headers),String.class);
        headers=new HttpHeaders(); headers.set("Idempotency-Key",UUID.randomUUID().toString());
        var target=http.postForEntity("/api/v1/conferences/"+secondId+"/registrations",
            new HttpEntity<>(RegistrationIntegrationTest.attendee(UUID.randomUUID()+"@example.test"),headers),String.class);
        String targetRegistration=json.readTree(target.getBody()).path("registrationId").asText();
        UUID targetId=db.queryForObject("select attendee_id from registration where id=?",UUID.class,UUID.fromString(targetRegistration));
        headers=new HttpHeaders(); headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set(HttpHeaders.COOKIE,own.getHeaders().getFirst(HttpHeaders.SET_COOKIE).split(";",2)[0]);
        var response=http.postForEntity("/api/v1/me/introductions",new HttpEntity<>("{\"targetAttendeeId\":\""+targetId+"\"}",headers),String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).contains("NOT_FOUND").doesNotContain("email","provider");
    }
}
