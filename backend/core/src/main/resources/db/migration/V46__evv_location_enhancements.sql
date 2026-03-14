-- EVV Location Enhancements: federal compliance fields
-- Adds noGpsReason, manualAddress to evv_record_location
-- Adds caregiverName snapshot to evv_record

-- Add no_gps_reason column - stores why GPS could not be captured (federal EVV requirement)
ALTER TABLE evv_record_location
    ADD COLUMN IF NOT EXISTS no_gps_reason VARCHAR(50);

-- Add manual_address column - free-form address for MANUAL location type (community/facility visits)
ALTER TABLE evv_record_location
    ADD COLUMN IF NOT EXISTS manual_address VARCHAR(500);

-- Add caregiver_name snapshot to evv_record for immutable audit trail
ALTER TABLE evv_record
    ADD COLUMN IF NOT EXISTS caregiver_name VARCHAR(255);

-- Comments for documentation
COMMENT ON COLUMN evv_record_location.no_gps_reason IS
    'Reason GPS location could not be captured; required when type != GPS per federal EVV regulations';
COMMENT ON COLUMN evv_record_location.manual_address IS
    'Manually entered address for MANUAL location type (e.g. community or facility visits)';
COMMENT ON COLUMN evv_record.caregiver_name IS
    'Snapshot of caregiver full name at time of visit; provides immutable audit trail independent of user record changes';
