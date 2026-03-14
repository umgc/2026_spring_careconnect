-- Enforce immutability: prevent UPDATE and DELETE on log/incident tables.
-- INSERT-only; records are append-only for audit integrity.

CREATE OR REPLACE FUNCTION reject_update_delete_immutable()
RETURNS TRIGGER AS $$
BEGIN
    RAISE EXCEPTION 'Updates and deletes are not allowed on immutable log/incident tables (%).', TG_TABLE_NAME;
END;
$$ LANGUAGE plpgsql;

-- activity_log
CREATE TRIGGER tr_activity_log_immutable
    BEFORE UPDATE OR DELETE ON activity_log
    FOR EACH ROW EXECUTE FUNCTION reject_update_delete_immutable();

-- behavioral_incidents
CREATE TRIGGER tr_behavioral_incidents_immutable
    BEFORE UPDATE OR DELETE ON behavioral_incidents
    FOR EACH ROW EXECUTE FUNCTION reject_update_delete_immutable();

-- incident_reports
CREATE TRIGGER tr_incident_reports_immutable
    BEFORE UPDATE OR DELETE ON incident_reports
    FOR EACH ROW EXECUTE FUNCTION reject_update_delete_immutable();

-- incident_actions
CREATE TRIGGER tr_incident_actions_immutable
    BEFORE UPDATE OR DELETE ON incident_actions
    FOR EACH ROW EXECUTE FUNCTION reject_update_delete_immutable();

-- client_events
CREATE TRIGGER tr_client_events_immutable
    BEFORE UPDATE OR DELETE ON client_events
    FOR EACH ROW EXECUTE FUNCTION reject_update_delete_immutable();
