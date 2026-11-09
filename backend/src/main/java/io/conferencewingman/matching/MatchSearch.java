package io.conferencewingman.matching;

import java.util.*;
import org.springframework.stereotype.Service;

@Service
public class MatchSearch {
    private final VectorIndex index;
    public MatchSearch(VectorIndex index) { this.index=index; }
    public List<UUID> find(UUID conferenceId, UUID attendeeId, float[] vector) {
        return index.search(conferenceId, attendeeId, vector, 3).stream().filter(id -> !id.equals(attendeeId)).distinct().limit(3).toList();
    }
}
