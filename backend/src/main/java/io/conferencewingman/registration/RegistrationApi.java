package io.conferencewingman.registration;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/conferences")
class RegistrationApi {
    private final RegistrationService service;
    RegistrationApi(RegistrationService service) { this.service = service; }

    record ConferenceInput(
        @NotBlank @Size(max=120) String name,
        @NotBlank @Size(max=2000) String description,
        @NotBlank @Size(max=200) String location,
        @NotNull Instant startDate,
        @NotNull Instant endDate) {
        ConferenceInput {
            name = trim(name);
            description = trim(description);
            location = trim(location);
        }
    }

    record AttendeeInput(
        @NotBlank @Size(max=120) String name,
        @NotBlank @Email @Size(max=254) String email,
        @NotBlank @Size(max=160) String jobTitle,
        @NotBlank @Size(max=160) String company,
        @NotBlank @Size(max=2000) String bio,
        @NotNull @Size(min=1,max=10) List<@NotBlank @Size(max=60) String> interests,
        @NotBlank @Size(max=1000) String networkingGoals) {
        AttendeeInput {
            name = trim(name);
            email = trim(email);
            jobTitle = trim(jobTitle);
            company = trim(company);
            bio = trim(bio);
            networkingGoals = trim(networkingGoals);
            if (interests != null) interests = interests.stream().map(RegistrationApi::trim).toList();
        }
    }

    private static String trim(String value) { return value == null ? null : value.trim(); }

    record ConferenceView(UUID id, String name, String description, String location, Instant startDate, Instant endDate) {}
    record ConferenceCreated(ConferenceView conference, String registrationUrl, String organizerToken) {}
    record ManagementView(ConferenceView conference, String registrationUrl, long registeredAttendeeCount) {}
    record RegistrationView(UUID registrationId, UUID conferenceId, String matchingStatus, String connectionsPath) {}

    @PostMapping
    ResponseEntity<ConferenceCreated> create(@Valid @RequestBody ConferenceInput input) {
        return ResponseEntity.status(201).cacheControl(CacheControl.noStore()).body(service.createConference(input));
    }

    @GetMapping
    ResponseEntity<List<ConferenceView>> list() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.conferences());
    }

    @GetMapping("/{id}")
    ConferenceView get(@PathVariable UUID id) { return service.conference(id); }

    @GetMapping("/{id}/management")
    ResponseEntity<ManagementView> manage(@PathVariable UUID id, @RequestHeader(value="Authorization",required=false) String authorization) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.management(id, authorization));
    }

    @PostMapping("/{id}/registrations")
    ResponseEntity<RegistrationView> register(@PathVariable UUID id, @RequestHeader("Idempotency-Key") UUID key,
                                             @Valid @RequestBody AttendeeInput input) {
        var result = service.register(id, key, input);
        var cookie = ResponseCookie.from("wingman_attendee", result.token()).httpOnly(true)
            .sameSite("Lax").secure(service.secureCookies()).path("/api/v1").maxAge(86400).build();
        return ResponseEntity.status(result.created() ? 201 : 200).cacheControl(CacheControl.noStore())
            .header(HttpHeaders.SET_COOKIE,cookie.toString()).body(result.view());
    }
}
