package io.conferencewingman.registration;

import jakarta.persistence.LockModeType;
import java.util.*;
import org.springframework.data.jpa.repository.*;

interface RegistrationRepository extends JpaRepository<Registration, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Registration> findByConferenceIdAndIdempotencyKey(UUID conferenceId, UUID idempotencyKey);
    long countByConferenceId(UUID conferenceId);
    Optional<Registration> findBySessionTokenHashAndSessionExpiresAtAfter(byte[] hash, java.time.Instant now);

    Optional<Registration> findById(UUID id);
    boolean existsByConferenceIdAndAttendeeId(UUID conferenceId, UUID attendeeId);
    Optional<Registration> findByConferenceIdAndAttendeeId(UUID conferenceId, UUID attendeeId);
}
