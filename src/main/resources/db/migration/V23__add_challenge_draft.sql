-- Quizzes still being set up after creation are drafts: editable, but not playable until finished
ALTER TABLE challenge ADD COLUMN IF NOT EXISTS draft BOOLEAN NOT NULL DEFAULT FALSE;
