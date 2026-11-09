package io.conferencewingman.registration;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
class Conference {
    @Id UUID id;
    @Column(nullable=false, length=120) String name;
    @Column(nullable=false, columnDefinition="text") String description;
    @Column(nullable=false, length=200) String location;
    @Column(nullable=false) Instant startDate;
    @Column(nullable=false) Instant endDate;
    @Column(nullable=false, unique=true) byte[] organizerTokenHash;
    @Column(nullable=false) Instant createdAt;
}
