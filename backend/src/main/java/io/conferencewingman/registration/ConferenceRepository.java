package io.conferencewingman.registration;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface ConferenceRepository extends JpaRepository<Conference, UUID> {
    List<Conference> findTop100ByEndDateAfterOrderByStartDateAsc(Instant now);
}
