-- V1 already introduced the durable status, retry and lease columns with registration.
-- Add claim ordering and make stale PROCESSING work discoverable after a restart.
CREATE INDEX registration_recovery_idx ON registration(matching_status, lease_until, next_attempt_at, created_at);
