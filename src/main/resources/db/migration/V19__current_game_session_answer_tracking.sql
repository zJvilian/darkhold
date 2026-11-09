-- Server-side answer tracking and orphan-session cleanup for the legacy game flow
ALTER TABLE current_game_session ADD COLUMN IF NOT EXISTS created_at BIGINT;
ALTER TABLE current_game_session ADD COLUMN IF NOT EXISTS answered_json TEXT;
ALTER TABLE current_game_session ADD COLUMN IF NOT EXISTS started_question_no INTEGER;
-- Backfill so existing rows aren't swept as ancient on the first cleanup run
UPDATE current_game_session SET created_at = (EXTRACT(EPOCH FROM NOW()) * 1000)::BIGINT WHERE created_at IS NULL;
