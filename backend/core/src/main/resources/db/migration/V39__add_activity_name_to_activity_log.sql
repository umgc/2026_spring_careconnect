ALTER TABLE activity_log
    ADD COLUMN IF NOT EXISTS activity_name VARCHAR(255);
