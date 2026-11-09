package io.conferencewingman;

import io.conferencewingman.matching.ElasticsearchVectorIndex;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

class ElasticsearchVectorIndexTest {
    @Test void searchUsesConferencePrefilterAndReturnsOnlyAttendeeIdentifiers() {
        var builder=RestClient.builder();
        var server=MockRestServiceServer.bindTo(builder).build();
        var index=new ElasticsearchVectorIndex(builder,"http://search.test","",2,"model-v1");
        UUID conference=UUID.randomUUID(), self=UUID.randomUUID(), peer=UUID.randomUUID();
        server.expect(requestTo("http://search.test/wingman-registration-v1/_search"))
            .andExpect(method(org.springframework.http.HttpMethod.POST))
            .andExpect(content().json("""
                {"size":3,"knn":{"field":"profileVector","query_vector":[0.1,0.2],"k":3,"num_candidates":50,
                 "filter":[{"term":{"conferenceId":"%s"}},{"bool":{"must_not":{"term":{"attendeeId":"%s"}}}}]}}
                """.formatted(conference,self)))
            .andRespond(withSuccess("{\"hits\":{\"hits\":[{\"_source\":{\"attendeeId\":\""+peer+"\"}}]}}",MediaType.APPLICATION_JSON));

        assertThat(index.search(conference,self,new float[]{0.1f,0.2f},3)).containsExactly(peer);
        server.verify();
    }

    @Test void refusesWrongDimensionBeforeNetworkCall() {
        var builder=RestClient.builder(); var server=MockRestServiceServer.bindTo(builder).build();
        var index=new ElasticsearchVectorIndex(builder,"http://search.test","",3,"model-v1");
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> index.search(UUID.randomUUID(),UUID.randomUUID(),new float[]{1f},3))
            .isInstanceOf(IllegalArgumentException.class);
        server.verify();
    }

    @Test void upsertsOnlyIdentifiersAndVectorIntoVersionedMapping() {
        var builder=RestClient.builder(); var server=MockRestServiceServer.bindTo(builder).build();
        var index=new ElasticsearchVectorIndex(builder,"http://search.test","",2,"model-v1");
        UUID registration=UUID.randomUUID(), conference=UUID.randomUUID(), attendee=UUID.randomUUID();
        server.expect(requestTo("http://search.test/wingman-registration-v1"))
            .andExpect(method(org.springframework.http.HttpMethod.HEAD)).andRespond(withStatus(org.springframework.http.HttpStatus.NOT_FOUND));
        server.expect(requestTo("http://search.test/wingman-registration-v1"))
            .andExpect(method(org.springframework.http.HttpMethod.PUT))
            .andExpect(content().json("""
                {"mappings":{"_meta":{"embeddingModel":"model-v1","dimensions":2},"properties":{"profileVector":{"type":"dense_vector","dims":2,"index":true,"similarity":"cosine"}}}}
                """))
            .andRespond(withSuccess("{}",MediaType.APPLICATION_JSON));
        server.expect(requestTo("http://search.test/wingman-registration-v1/_doc/"+registration+"?refresh=wait_for"))
            .andExpect(method(org.springframework.http.HttpMethod.PUT))
            .andExpect(content().json("""
                {"registrationId":"%s","conferenceId":"%s","attendeeId":"%s","profileVector":[0.1,0.2]}
                """.formatted(registration,conference,attendee)))
            .andRespond(withSuccess("{}",MediaType.APPLICATION_JSON));

        index.upsert(registration,conference,attendee,new float[]{0.1f,0.2f});
        server.verify();
    }

    @Test void sendsCloudApiKeyFromConfigurationWithoutLoggingIt() {
        var builder=RestClient.builder(); var server=MockRestServiceServer.bindTo(builder).build();
        var index=new ElasticsearchVectorIndex(builder,"https://search.test:443","test-only-key",2,"model-v1");
        UUID conference=UUID.randomUUID(), self=UUID.randomUUID();
        server.expect(requestTo("https://search.test:443/wingman-registration-v1/_search"))
            .andExpect(header("Authorization","ApiKey test-only-key"))
            .andRespond(withSuccess("{\"hits\":{\"hits\":[]}}",MediaType.APPLICATION_JSON));
        assertThat(index.search(conference,self,new float[]{0.1f,0.2f},3)).isEmpty();
        server.verify();
    }
}
