package io.conferencewingman;

import io.conferencewingman.matching.MatchSearch;
import io.conferencewingman.matching.VectorIndex;
import java.util.*;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MatchSearchTest {
    @Test void asksIndexForConferenceFilteredPeersAndExcludesSelf() {
        UUID conference = UUID.randomUUID(), self = UUID.randomUUID();
        var index = new RecordingIndex(List.of(self, UUID.randomUUID()));
        var search = new MatchSearch(index);

        assertThat(search.find(conference, self, new float[]{0.2f, 0.4f})).hasSize(1).doesNotContain(self);
        assertThat(index.conference).isEqualTo(conference);
        assertThat(index.excludedAttendee).isEqualTo(self);
    }

    @Test void zeroOneAndTwoResultsAreValid() {
        UUID conference = UUID.randomUUID(), self = UUID.randomUUID();
        for (int count = 0; count <= 2; count++) {
            var ids = java.util.stream.IntStream.range(0, count).mapToObj(i -> UUID.randomUUID()).toList();
            assertThat(new MatchSearch(new RecordingIndex(ids)).find(conference, self, new float[]{1f})).hasSize(count);
        }
    }

    private static class RecordingIndex implements VectorIndex {
        final List<UUID> results; UUID conference; UUID excludedAttendee;
        RecordingIndex(List<UUID> results) { this.results=results; }
        public void upsert(UUID registration, UUID conference, UUID attendee, float[] vector) {}
        public List<UUID> search(UUID conference, UUID excludedAttendee, float[] vector, int limit) {
            this.conference=conference; this.excludedAttendee=excludedAttendee; return results;
        }
    }
}
