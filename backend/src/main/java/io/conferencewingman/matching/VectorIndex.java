package io.conferencewingman.matching;

import java.util.List;
import java.util.UUID;

public interface VectorIndex {
    void upsert(UUID registrationId, UUID conferenceId, UUID attendeeId, float[] vector);
    List<UUID> search(UUID conferenceId, UUID excludedAttendeeId, float[] vector, int limit);
}
