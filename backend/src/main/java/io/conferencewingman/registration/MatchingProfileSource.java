package io.conferencewingman.registration;

import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MatchingProfileSource {
    private final RegistrationRepository registrations;
    private final AttendeeRepository attendees;
    public MatchingProfileSource(RegistrationRepository registrations, AttendeeRepository attendees) {
        this.registrations=registrations; this.attendees=attendees;
    }
    @Transactional(readOnly=true)
    public Optional<Profile> find(UUID registrationId) {
        return registrations.findById(registrationId).flatMap(r -> attendees.findById(r.attendeeId).map(a ->
            new Profile(r.id,r.conferenceId,a.id,a.name,a.jobTitle,a.company,a.bio,a.networkingGoals,List.copyOf(a.interests))));
    }
    @Transactional(readOnly=true)
    public List<UUID> eligibleAttendees(UUID conferenceId, UUID excludedAttendee, List<UUID> attendeeIds) {
        return attendeeIds.stream().filter(id -> !id.equals(excludedAttendee))
            .filter(id -> registrations.existsByConferenceIdAndAttendeeId(conferenceId,id)).distinct().limit(3).toList();
    }
    @Transactional(readOnly=true)
    public List<Profile> eligibleProfiles(UUID conferenceId, UUID excludedAttendee, List<UUID> attendeeIds) {
        return eligibleAttendees(conferenceId, excludedAttendee, attendeeIds).stream()
            .map(id -> attendees.findById(id).orElse(null))
            .filter(Objects::nonNull)
            .map(a -> registrations.findByConferenceIdAndAttendeeId(conferenceId, a.id)
                .map(r -> new Profile(r.id,conferenceId,a.id,a.name,a.jobTitle,a.company,a.bio,a.networkingGoals,List.copyOf(a.interests))))
            .flatMap(Optional::stream).limit(3).toList();
    }
    @Transactional(readOnly=true)
    public Optional<Profile> findByAttendeeId(UUID conferenceId, UUID attendeeId) {
        return registrations.findByConferenceIdAndAttendeeId(conferenceId,attendeeId).flatMap(r -> attendees.findById(attendeeId).map(a ->
            new Profile(r.id,conferenceId,a.id,a.name,a.jobTitle,a.company,a.bio,a.networkingGoals,List.copyOf(a.interests))));
    }
    public record Profile(UUID registrationId, UUID conferenceId, UUID attendeeId, String name, String jobTitle,
        String company, String bio, String networkingGoals, List<String> interests) {
        public String embeddingText() { return String.join("\n", List.of("Role: "+jobTitle,"Company: "+company,
            "Bio: "+bio,"Interests: "+String.join(", ",interests),"Networking goals: "+networkingGoals)); }
    }
}
