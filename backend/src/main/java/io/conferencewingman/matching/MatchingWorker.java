package io.conferencewingman.matching;

import io.conferencewingman.ai.EmbeddingClient;
import io.conferencewingman.registration.MatchingProfileSource;
import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

@Component
public class MatchingWorker {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaction;
    private final MatchingProfileSource profiles;
    private final EmbeddingClient embeddings;
    private final VectorIndex index;
    private final MatchSearch search;
    private final int maxAttempts;

    public MatchingWorker(JdbcTemplate jdbc, org.springframework.transaction.PlatformTransactionManager manager,
        MatchingProfileSource profiles, EmbeddingClient embeddings, VectorIndex index, MatchSearch search,
        @Value("${wingman.matching.max-attempts:3}") int maxAttempts) {
        this.jdbc=jdbc; this.transaction=new TransactionTemplate(manager); this.profiles=profiles;
        this.embeddings=embeddings; this.index=index; this.search=search; this.maxAttempts=maxAttempts;
    }

    @Scheduled(fixedDelayString="${wingman.matching.poll-ms:1000}")
    public void poll() { processOne(); }

    public boolean processOne() {
        if (!embeddings.configured()) return false;
        UUID id=transaction.execute(status -> {
            jdbc.update("update registration set matching_status='FAILED', lease_until=null, next_attempt_at=null, safe_error_code='MATCHING_UNAVAILABLE', updated_at=now() where matching_status='PROCESSING' and lease_until<=now() and attempts >= ?", maxAttempts);
            var rows=jdbc.query("select id from registration where ((matching_status='PENDING' and (next_attempt_at is null or next_attempt_at<=now())) or (matching_status='PROCESSING' and lease_until<=now())) and attempts < ? order by created_at for update skip locked limit 1",
                (rs,row)->rs.getObject(1,UUID.class), maxAttempts);
            if(rows.isEmpty()) return null;
            UUID work=rows.get(0);
            jdbc.update("update registration set matching_status='PROCESSING', attempts=attempts+1, lease_until=?, updated_at=now() where id=?",
                Instant.now().plusSeconds(45),work);
            return work;
        });
        if(id==null) return false;
        try {
            var profile=profiles.find(id).orElseThrow();
            float[] vector=embeddings.embed(profile.embeddingText());
            index.upsert(profile.registrationId(),profile.conferenceId(),profile.attendeeId(),vector);
            List<UUID> candidates=search.find(profile.conferenceId(),profile.attendeeId(),vector);
            profiles.eligibleAttendees(profile.conferenceId(),profile.attendeeId(),candidates);
            jdbc.update("update registration set matching_status='READY', lease_until=null, next_attempt_at=null, safe_error_code=null, updated_at=now() where id=?",id);
        } catch(RuntimeException failure) {
            jdbc.update("update registration set matching_status=case when attempts >= ? then 'FAILED' else 'PENDING' end, lease_until=null, next_attempt_at=case when attempts >= ? then null else now() + interval '5 seconds' end, safe_error_code='MATCHING_UNAVAILABLE', updated_at=now() where id=?",
                maxAttempts,maxAttempts,id);
        }
        return true;
    }
}
