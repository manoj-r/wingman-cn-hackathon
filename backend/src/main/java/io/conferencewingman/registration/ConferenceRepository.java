package io.conferencewingman.registration;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface ConferenceRepository extends JpaRepository<Conference, UUID> {}
