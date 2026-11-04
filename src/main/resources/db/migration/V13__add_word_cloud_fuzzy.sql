-- Add quiz-level toggle for fuzzy typo matching in WORD_CLOUD questions
ALTER TABLE challenge ADD COLUMN word_cloud_fuzzy BOOLEAN DEFAULT TRUE;
