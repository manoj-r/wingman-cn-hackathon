package io.conferencewingman;

import io.conferencewingman.ai.EmbeddingClient;
import io.conferencewingman.matching.*;
import io.conferencewingman.registration.MatchingProfileSource;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class MatchingRecoveryTest {
    @Test void claimsPersistedPendingRegistrationAndCompletesIdempotentIndexing() {
        var jdbc=mock(JdbcTemplate.class);
        var manager=mock(PlatformTransactionManager.class);
        var profiles=mock(MatchingProfileSource.class);
        var embeddings=mock(EmbeddingClient.class);
        var index=mock(VectorIndex.class);
        var search=mock(MatchSearch.class);
        var matchCandidates=mock(MatchCandidateStore.class);
        var recommendations=mock(RecommendationService.class);
        UUID id=UUID.randomUUID(), conference=UUID.randomUUID(), attendee=UUID.randomUUID();
        var profile=new MatchingProfileSource.Profile(id,conference,attendee,"Riley","Engineer","Example",
            "Building reliable systems","Find platform peers",List.of("Kubernetes"));
        when(jdbc.query(contains("matching_status='PENDING'"),any(org.springframework.jdbc.core.RowMapper.class),eq(3)))
            .thenReturn(List.of(id));
        when(profiles.find(id)).thenReturn(Optional.of(profile));
        when(embeddings.configured()).thenReturn(true);
        when(embeddings.embed(anyString())).thenReturn(new float[]{0.2f,0.8f});
        UUID eligible=UUID.randomUUID();
        when(search.find(conference,attendee,new float[]{0.2f,0.8f})).thenReturn(List.of(eligible));
        when(profiles.eligibleAttendees(conference,attendee,List.of(eligible))).thenReturn(List.of(eligible));
        var worker=new MatchingWorker(jdbc,manager,profiles,embeddings,index,search,matchCandidates,recommendations,3);

        assertThat(worker.processOne()).isTrue();
        verify(jdbc).update(contains("matching_status='PROCESSING'"),any(),eq(id));
        verify(index).upsert(id,conference,attendee,new float[]{0.2f,0.8f});
        var completion=inOrder(matchCandidates,recommendations,jdbc);
        completion.verify(matchCandidates).replace(id,conference,attendee,List.of(eligible));
        completion.verify(recommendations).generate(conference,attendee,List.of(eligible));
        completion.verify(jdbc).update(contains("matching_status='READY'"),eq(id));
    }

    @Test void terminallyFailsExpiredFinalAttemptBeforeClaimingMoreWork() {
        var jdbc=mock(JdbcTemplate.class);
        var manager=mock(PlatformTransactionManager.class);
        var profiles=mock(MatchingProfileSource.class);
        var embeddings=mock(EmbeddingClient.class);
        var index=mock(VectorIndex.class);
        var search=mock(MatchSearch.class);
        var matchCandidates=mock(MatchCandidateStore.class);
        var recommendations=mock(RecommendationService.class);
        when(embeddings.configured()).thenReturn(true);
        when(jdbc.query(contains("matching_status='PENDING'"),any(org.springframework.jdbc.core.RowMapper.class),eq(3)))
            .thenReturn(List.of());
        var worker=new MatchingWorker(jdbc,manager,profiles,embeddings,index,search,matchCandidates,recommendations,3);

        assertThat(worker.processOne()).isFalse();

        var order=inOrder(jdbc);
        order.verify(jdbc).update(contains("matching_status='FAILED'"),eq(3));
        order.verify(jdbc).query(contains("matching_status='PENDING'"),any(org.springframework.jdbc.core.RowMapper.class),eq(3));
    }

    @Test void providerFailureReturnsDurableWorkToRetryWithSafeCode() {
        var jdbc=mock(JdbcTemplate.class); var manager=mock(PlatformTransactionManager.class);
        var profiles=mock(MatchingProfileSource.class); var embeddings=mock(EmbeddingClient.class);
        var index=mock(VectorIndex.class); var search=mock(MatchSearch.class); UUID id=UUID.randomUUID();
        when(jdbc.query(contains("matching_status='PENDING'"),any(org.springframework.jdbc.core.RowMapper.class),eq(3))).thenReturn(List.of(id));
        when(profiles.find(id)).thenReturn(Optional.of(new MatchingProfileSource.Profile(id,UUID.randomUUID(),UUID.randomUUID(),
            "Riley","Engineer","Example","Bio","Goal",List.of("Kubernetes"))));
        when(embeddings.configured()).thenReturn(true);
        when(embeddings.embed(anyString())).thenThrow(new EmbeddingClient.EmbeddingException("private provider detail"));
        var matchCandidates=mock(MatchCandidateStore.class);
        var recommendations=mock(RecommendationService.class);
        var worker=new MatchingWorker(jdbc,manager,profiles,embeddings,index,search,matchCandidates,recommendations,3);

        assertThat(worker.processOne()).isTrue();
        verify(jdbc).update(contains("matching_status=case when attempts >= ? then 'FAILED' else 'PENDING' end"),eq(3),eq(3),eq(id));
        verify(index,never()).upsert(any(),any(),any(),any());
    }
}
