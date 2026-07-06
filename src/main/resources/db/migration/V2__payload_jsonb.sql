ALTER TABLE saga ALTER COLUMN payload TYPE jsonb USING payload::jsonb;
