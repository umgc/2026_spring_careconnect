-- Hardening: Remove legacy table replaced by normalized telemetry_events in V34
-- This cleans up the database schema for the Fargate migration.
DROP TABLE IF EXISTS feature_telemetry_event CASCADE;