package io.conferencewingman.matching;

import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class MatchCandidateStore {
    private final JdbcTemplate jdbc;

    public MatchCandidateStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional
    public void replace(UUID registrationId, UUID conferenceId, UUID ownerAttendeeId, List<UUID> attendeeIds) {
        jdbc.update("delete from registration_match where registration_id=?", registrationId);
        for (UUID attendeeId : attendeeIds) {
            jdbc.update("insert into registration_match(registration_id,attendee_id) "
                    + "select ?, target.attendee_id from registration target "
                    + "where target.conference_id=? and target.attendee_id=? and target.attendee_id<>? "
                    + "on conflict (registration_id,attendee_id) do nothing",
                registrationId, conferenceId, attendeeId, ownerAttendeeId);
        }
    }

    @Transactional(readOnly = true)
    public List<UUID> findEligible(UUID registrationId) {
        return jdbc.query("select m.attendee_id from registration_match m "
                        + "join registration owner on owner.id=m.registration_id "
                        + "join registration target on target.conference_id=owner.conference_id "
                        + "and target.attendee_id=m.attendee_id "
                        + "where owner.id=? and target.attendee_id<>owner.attendee_id "
                        + "order by m.created_at, m.attendee_id limit 3",
                (rs, row) -> rs.getObject(1, UUID.class), registrationId);
    }
}
