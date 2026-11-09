package io.conferencewingman.registration;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface AttendeeRepository extends JpaRepository<Attendee, UUID> {}
