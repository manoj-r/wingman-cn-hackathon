package io.conferencewingman;

import io.conferencewingman.matching.MatchCandidateStore;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class MatchCandidateStoreTest {
    @Test void storesOnlySameConferenceNonSelfCandidatesAndProvidesAnEligibleReader() {
        var jdbc = mock(JdbcTemplate.class);
        var store = new MatchCandidateStore(jdbc);
        UUID registration = UUID.randomUUID();
        UUID conference = UUID.randomUUID();
        UUID owner = UUID.randomUUID();
        UUID candidate = UUID.randomUUID();

        store.replace(registration, conference, owner, List.of(candidate, owner));

        verify(jdbc).update("delete from registration_match where registration_id=?", registration);
        verify(jdbc).update(contains("target.conference_id=?"), eq(registration), eq(conference), eq(candidate), eq(owner));
        verify(jdbc).update(contains("target.attendee_id<>?"), eq(registration), eq(conference), eq(owner), eq(owner));

        when(jdbc.query(contains("target.conference_id=owner.conference_id"), any(org.springframework.jdbc.core.RowMapper.class), eq(registration)))
                .thenReturn(List.of(candidate));
        assertThat(store.findEligible(registration)).containsExactly(candidate);
    }
}
