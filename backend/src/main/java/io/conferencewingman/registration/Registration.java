package io.conferencewingman.registration;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
class Registration {
    @Id UUID id;
    @Column(nullable=false) UUID conferenceId;
    @Column(nullable=false, unique=true) UUID attendeeId;
    @Column(nullable=false, length=254) String normalizedEmail;
    @Column(nullable=false) UUID idempotencyKey;
    @Column(nullable=false) byte[] requestHash;
    @Column(nullable=false, unique=true) byte[] sessionTokenHash;
    @Column(nullable=false) Instant sessionExpiresAt;
    @Column(nullable=false, length=20) String matchingStatus = "PENDING";
    @Column(nullable=false) int attempts;
    Instant leaseUntil;
    Instant nextAttemptAt;
    @Column(length=80) String safeErrorCode;
    @Column(nullable=false) Instant createdAt;
    @Column(nullable=false) Instant updatedAt;
}
