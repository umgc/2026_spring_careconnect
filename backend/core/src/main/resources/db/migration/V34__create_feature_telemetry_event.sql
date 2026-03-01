CREATE TABLE feature_telemetry_event (
  id BIGSERIAL PRIMARY KEY,
  event_name VARCHAR(128) NOT NULL,
  event_time TIMESTAMPTZ NOT NULL DEFAULT now(),

  actor_user_id BIGINT,
  patient_id BIGINT,

  trace_id VARCHAR(64),
  span_id VARCHAR(32),

  device_info JSONB,
  details JSONB
);

CREATE INDEX idx_feature_telemetry_event_time
  ON feature_telemetry_event (event_time DESC);

CREATE INDEX idx_feature_telemetry_event_name_time
  ON feature_telemetry_event (event_name, event_time DESC);
