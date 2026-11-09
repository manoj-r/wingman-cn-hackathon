package io.conferencewingman.matching;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class ElasticsearchVectorIndex implements VectorIndex {
    private static final String INDEX = "wingman-registration-v1";
    private final RestClient http;
    private final int dimensions;
    private final String model;

    public ElasticsearchVectorIndex(RestClient.Builder builder,
        @Value("${ELASTICSEARCH_URL:http://localhost:9200}") String url,
        @Value("${ELASTICSEARCH_API_KEY:}") String apiKey,
        @Value("${wingman.embedding.dimensions:0}") int dimensions,
        @Value("${wingman.embedding.model:unconfigured}") String model) {
        var client=builder.clone().baseUrl(url);
        if(apiKey!=null&&!apiKey.isBlank()) client.defaultHeader("Authorization","ApiKey "+apiKey);
        this.http=client.build(); this.dimensions=dimensions; this.model=model;
    }

    @Override public void upsert(UUID registration, UUID conference, UUID attendee, float[] vector) {
        requireDimensions(vector);
        ensureIndex();
        Map<String,Object> document=Map.of("registrationId",registration.toString(),"conferenceId",conference.toString(),
            "attendeeId",attendee.toString(),"profileVector",vector);
        http.put().uri("/"+INDEX+"/_doc/"+registration+"?refresh=wait_for").body(document).retrieve().toBodilessEntity();
    }

    @Override public List<UUID> search(UUID conference, UUID excludedAttendee, float[] vector, int limit) {
        requireDimensions(vector);
        Map<String,Object> knn=Map.of("field","profileVector","query_vector",vector,"k",Math.min(3,limit),
            "num_candidates",50,"filter",List.of(Map.of("term",Map.of("conferenceId",conference.toString())),
                Map.of("bool",Map.of("must_not",Map.of("term",Map.of("attendeeId",excludedAttendee.toString()))))));
        JsonNode result=http.post().uri("/"+INDEX+"/_search").body(Map.of("size",Math.min(3,limit),"knn",knn)).retrieve().body(JsonNode.class);
        List<UUID> ids=new ArrayList<>();
        if(result!=null) for(JsonNode hit:result.path("hits").path("hits")) {
            try { ids.add(UUID.fromString(hit.path("_source").path("attendeeId").asText())); } catch(IllegalArgumentException ignored) { }
        }
        return ids;
    }

    private void ensureIndex() {
        try { http.head().uri("/"+INDEX).retrieve().toBodilessEntity(); }
        catch(org.springframework.web.client.HttpClientErrorException.NotFound missing) {
            http.put().uri("/"+INDEX).body(Map.of("mappings",Map.of("_meta",Map.of("embeddingModel",model,"dimensions",dimensions),
                "properties",Map.of("registrationId",Map.of("type","keyword"),"conferenceId",Map.of("type","keyword"),
                    "attendeeId",Map.of("type","keyword"),"profileVector",Map.of("type","dense_vector","dims",dimensions,"index",true,"similarity","cosine"))))).retrieve().toBodilessEntity();
        }
    }

    private void requireDimensions(float[] vector) {
        if(dimensions<=0 || vector==null || vector.length!=dimensions) throw new IllegalArgumentException("Embedding dimension mismatch");
    }
}
