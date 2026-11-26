-- Optional image shown when a question's answer is revealed (universal across all question types)
ALTER TABLE question_set ADD COLUMN IF NOT EXISTS answer_image_url VARCHAR(500);
