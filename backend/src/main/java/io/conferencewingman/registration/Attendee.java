package io.conferencewingman.registration;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.*;

@Entity
class Attendee {
    @Id UUID id;
    @Column(nullable=false, length=120) String name;
    @Column(nullable=false, length=254) String email;
    @Column(nullable=false, length=160) String jobTitle;
    @Column(nullable=false, length=160) String company;
    @Column(nullable=false, columnDefinition="text") String bio;
    @Column(nullable=false, columnDefinition="text") String networkingGoals;
    @Column(nullable=false) Instant createdAt;
    @ElementCollection
    @CollectionTable(name="attendee_interest", joinColumns=@JoinColumn(name="attendee_id"))
    @OrderColumn(name="ordinal")
    @Column(name="interest", nullable=false, length=60)
    List<String> interests = new ArrayList<>();
}
