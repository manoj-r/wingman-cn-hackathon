CREATE TABLE registration_match (
    registration_id uuid NOT NULL REFERENCES registration(id) ON DELETE CASCADE,
    attendee_id uuid NOT NULL REFERENCES attendee(id) ON DELETE CASCADE,
    explanation varchar(500),
    conversation_starter varchar(300),
    created_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (registration_id, attendee_id)
);
CREATE INDEX registration_match_target_idx ON registration_match(attendee_id);
