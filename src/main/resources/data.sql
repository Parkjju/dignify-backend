INSERT INTO cron_state (job_name, last_processed_id)
VALUES ('track_collect', NULL)
    ON CONFLICT (job_name) DO NOTHING;