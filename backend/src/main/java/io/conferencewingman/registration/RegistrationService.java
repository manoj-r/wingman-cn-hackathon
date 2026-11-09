package io.conferencewingman.registration;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import io.conferencewingman.observability.RegistrationTelemetry;

import static io.conferencewingman.registration.RegistrationApi.*;

@Service
class RegistrationService {
    private final ConferenceRepository conferences;
    private final AttendeeRepository attendees;
    private final RegistrationRepository registrations;
    private final TransactionTemplate transaction;
    private final ObjectMapper json;
    private final String publicUrl;
    private final RegistrationTelemetry telemetry;
    private final SecureRandom random = new SecureRandom();

    RegistrationService(ConferenceRepository conferences, AttendeeRepository attendees,
                        RegistrationRepository registrations, PlatformTransactionManager manager,
                        ObjectMapper json, RegistrationTelemetry telemetry,
                        @Value("${PUBLIC_APP_URL:http://localhost:3000}") String publicUrl) {
        this.conferences=conferences; this.attendees=attendees; this.registrations=registrations;
        this.transaction=new TransactionTemplate(manager); this.json=json; this.telemetry=telemetry;
        if (!publicUrl.matches("https?://[^?#]+")) throw new IllegalArgumentException("PUBLIC_APP_URL must be an HTTP(S) base URL without query or fragment");
        this.publicUrl=publicUrl.replaceAll("/+$", "");
    }

    ConferenceCreated createConference(ConferenceInput input) {
        if (input.endDate().isBefore(input.startDate())) throw new ApiError(400,"VALIDATION_ERROR","End date must follow start date.");
        return transaction.execute(status -> {
            String token=token();
            Conference c=new Conference(); c.id=UUID.randomUUID(); c.name=input.name().trim();
            c.description=input.description().trim(); c.location=input.location().trim();
            c.startDate=input.startDate(); c.endDate=input.endDate(); c.createdAt=Instant.now(); c.organizerTokenHash=hash(token);
            conferences.saveAndFlush(c);
            return new ConferenceCreated(view(c),registrationUrl(c.id),token);
        });
    }

    ConferenceView conference(UUID id) { return view(findConference(id)); }

    ManagementView management(UUID id, String authorization) {
        // Generic denial avoids exposing whether a private management target exists.
        var c=conferences.findById(id).orElseThrow(() -> new ApiError(401,"UNAUTHORIZED","Private organizer token required."));
        String token=authorization != null && authorization.startsWith("Bearer ") ? authorization.substring(7) : "";
        if (token.length()>256 || !MessageDigest.isEqual(c.organizerTokenHash,hash(token)))
            throw new ApiError(401,"UNAUTHORIZED","Private organizer token required.");
        return new ManagementView(view(c),registrationUrl(id),registrations.countByConferenceId(id));
    }

    record Result(RegistrationView view, String token, boolean created) {}

    Result register(UUID conference, UUID key, AttendeeInput supplied) {
        return telemetry.record(() -> registerTraced(conference,key,supplied), result ->
            result.created() ? "created" : "replayed");
    }

    private Result registerTraced(UUID conference, UUID key, AttendeeInput supplied) {
        AttendeeInput input=normalize(supplied);
        byte[] digest;
        try { digest=hash(json.writeValueAsString(input)); }
        catch (JsonProcessingException error) { throw new IllegalStateException("Cannot encode registration request"); }
        try {
            return transaction.execute(status -> {
                findConference(conference);
                var existing=registrations.findByConferenceIdAndIdempotencyKey(conference,key);
                if (existing.isPresent()) {
                    return replay(existing.get(),digest);
                }
                Instant now=Instant.now();
                Attendee a=new Attendee(); a.id=UUID.randomUUID(); a.name=input.name(); a.email=input.email();
                a.jobTitle=input.jobTitle(); a.company=input.company(); a.bio=input.bio();
                a.networkingGoals=input.networkingGoals(); a.interests=new ArrayList<>(input.interests()); a.createdAt=now;
                attendees.save(a);
                Registration r=new Registration(); r.id=UUID.randomUUID(); r.conferenceId=conference; r.attendeeId=a.id;
                r.normalizedEmail=input.email(); r.idempotencyKey=key; r.requestHash=digest; r.createdAt=now; r.updatedAt=now;
                String token=rotate(r); registrations.saveAndFlush(r);
                return result(r,token,true);
            });
        } catch (DataIntegrityViolationException conflict) {
            // The failed transaction has fully rolled back, including its attendee insert.
            return transaction.execute(status -> registrations.findByConferenceIdAndIdempotencyKey(conference,key)
                .map(r -> replay(r,digest)).orElseThrow(RegistrationService::conflict));
        }
    }

    private Result replay(Registration r, byte[] digest) {
        if (!MessageDigest.isEqual(r.requestHash,digest)) throw conflict();
        String token=rotate(r); registrations.saveAndFlush(r); return result(r,token,false);
    }

    private String rotate(Registration r) {
        String token=token(); r.sessionTokenHash=hash(token); r.sessionExpiresAt=Instant.now().plus(Duration.ofHours(24));
        r.updatedAt=Instant.now(); return token;
    }

    private Result result(Registration r, String token, boolean created) {
        return new Result(new RegistrationView(r.id,r.conferenceId,r.matchingStatus,"/connections"),token,created);
    }

    private AttendeeInput normalize(AttendeeInput input) {
        var interests=input.interests().stream().map(String::trim).toList();
        if (new HashSet<>(interests).size()!=interests.size()) throw new ApiError(400,"VALIDATION_ERROR","Interests must be distinct.");
        return new AttendeeInput(input.name().trim(),input.email().trim().toLowerCase(Locale.ROOT),input.jobTitle().trim(),
            input.company().trim(),input.bio().trim(),interests,input.networkingGoals().trim());
    }

    private Conference findConference(UUID id) { return conferences.findById(id).orElseThrow(() -> new ApiError(404,"NOT_FOUND","Conference not found.")); }
    private ConferenceView view(Conference c) { return new ConferenceView(c.id,c.name,c.description,c.location,c.startDate,c.endDate); }
    private String registrationUrl(UUID id) { return publicUrl+"/conferences/"+id+"/register"; }
    boolean secureCookies() { return publicUrl.startsWith("https://"); }
    private String token() { byte[] value=new byte[32]; random.nextBytes(value); return Base64.getUrlEncoder().withoutPadding().encodeToString(value); }
    private static ApiError conflict() { return new ApiError(409,"REGISTRATION_CONFLICT","Registration conflicts with an existing submission."); }
    static byte[] hash(String value) {
        try { return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
}
