package io.conferencewingman.matching;

import java.util.*;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

@RestController
@RequestMapping("/api/v1/me")
class MatchController {
    private final JdbcTemplate jdbc;
    private final RecommendationService recommendations;
    private final IntroductionService introductions;
    MatchController(JdbcTemplate jdbc,RecommendationService recommendations,IntroductionService introductions){this.jdbc=jdbc;this.recommendations=recommendations;this.introductions=introductions;}

    @GetMapping("/matches")
    ResponseEntity<?> matches(jakarta.servlet.http.HttpServletRequest request) {
        UUID registration=(UUID)request.getAttribute("wingman.registrationId");
        UUID conference=(UUID)request.getAttribute("wingman.conferenceId");
        var state=jdbc.queryForMap("select matching_status,safe_error_code from registration where id=? and conference_id=?",registration,conference);
        String status=(String)state.get("matching_status");
        if(!"READY".equals(status)) {
            Map<String,Object> body=new LinkedHashMap<>();
            body.put("registrationId",registration); body.put("matchingStatus",status); body.put("generationMode",null);
            body.put("matches",List.of()); body.put("availableMatchCount",0); body.put("retryable",true);
            if("FAILED".equals(status)) body.put("failureCode",Objects.toString(state.get("safe_error_code"),"MATCHING_UNAVAILABLE"));
            else body.put("pollAfterSeconds",2);
            return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body);
        }
        var matches=jdbc.query("select rm.attendee_id,a.name,a.job_title,a.company,rm.explanation,rm.conversation_starter "
                        + "from registration_match rm join registration owner on owner.id=rm.registration_id "
                        + "join registration target on target.conference_id=owner.conference_id "
                        + "and target.attendee_id=rm.attendee_id join attendee a on a.id=target.attendee_id "
                        + "where rm.registration_id=? and target.attendee_id<>owner.attendee_id "
                        + "order by rm.created_at limit 3",
            (rs,row)->new MatchRow((UUID)rs.getObject("attendee_id"),rs.getString("name"),rs.getString("job_title"),rs.getString("company"),rs.getString("explanation"),rs.getString("conversation_starter")),registration);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(Map.of("registrationId",registration,"matchingStatus","READY","generationMode","llm","matches",matches,"availableMatchCount",matches.size(),"retryable",false,"traceId",UUID.randomUUID().toString()));
    }

    @PostMapping("/matches/retry")
    ResponseEntity<?> retry(jakarta.servlet.http.HttpServletRequest request) {
        UUID registration=(UUID)request.getAttribute("wingman.registrationId");
        int updated=jdbc.update("update registration set matching_status='PENDING', attempts=0, lease_until=null, next_attempt_at=now(), safe_error_code=null, updated_at=now() where id=? and matching_status='FAILED'",registration);
        String status=updated>0?"PENDING":jdbc.queryForObject("select matching_status from registration where id=?",String.class,registration);
        return ResponseEntity.accepted().cacheControl(CacheControl.noStore()).body(Map.of("registrationId",registration,"matchingStatus",status));
    }

    @PostMapping("/introductions")
    ResponseEntity<?> introduction(@Valid @RequestBody IntroductionRequest request,jakarta.servlet.http.HttpServletRequest servlet) {
        UUID registration=(UUID)servlet.getAttribute("wingman.registrationId");
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(introductions.generate(registration,request.targetAttendeeId()));
    }
    record IntroductionRequest(@NotNull UUID targetAttendeeId){}
    record MatchRow(UUID attendeeId,String name,String jobTitle,String company,String explanation,String conversationStarter){}
}
