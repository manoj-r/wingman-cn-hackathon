CREATE TABLE conference (
    id uuid PRIMARY KEY,
    name varchar(120) NOT NULL,
    description text NOT NULL,
    location varchar(200) NOT NULL,
    start_date timestamptz NOT NULL,
    end_date timestamptz NOT NULL CHECK (end_date >= start_date),
    organizer_token_hash bytea NOT NULL UNIQUE CHECK (octet_length(organizer_token_hash) = 32),
    created_at timestamptz NOT NULL
);

CREATE TABLE attendee (
    id uuid PRIMARY KEY,
    name varchar(120) NOT NULL,
    email varchar(254) NOT NULL,
    job_title varchar(160) NOT NULL,
    company varchar(160) NOT NULL,
    bio text NOT NULL,
    networking_goals text NOT NULL,
    created_at timestamptz NOT NULL
);

CREATE TABLE attendee_interest (
    attendee_id uuid NOT NULL REFERENCES attendee(id) ON DELETE CASCADE,
    ordinal integer NOT NULL,
    interest varchar(60) NOT NULL,
    PRIMARY KEY (attendee_id, ordinal),
    UNIQUE (attendee_id, interest)
);

CREATE TABLE registration (
    id uuid PRIMARY KEY,
    conference_id uuid NOT NULL REFERENCES conference(id),
    attendee_id uuid NOT NULL UNIQUE REFERENCES attendee(id),
    normalized_email varchar(254) NOT NULL,
    idempotency_key uuid NOT NULL,
    request_hash bytea NOT NULL CHECK (octet_length(request_hash) = 32),
    session_token_hash bytea NOT NULL UNIQUE CHECK (octet_length(session_token_hash) = 32),
    session_expires_at timestamptz NOT NULL,
    matching_status varchar(20) NOT NULL DEFAULT 'PENDING' CHECK (matching_status IN ('PENDING','PROCESSING','READY','FAILED')),
    attempts integer NOT NULL DEFAULT 0 CHECK (attempts >= 0),
    lease_until timestamptz,
    next_attempt_at timestamptz,
    safe_error_code varchar(80),
    created_at timestamptz NOT NULL,
    updated_at timestamptz NOT NULL,
    UNIQUE (conference_id, attendee_id),
    UNIQUE (conference_id, normalized_email),
    UNIQUE (conference_id, idempotency_key)
);
CREATE INDEX registration_conference_idx ON registration(conference_id);
CREATE INDEX registration_pending_idx ON registration(matching_status, next_attempt_at);
