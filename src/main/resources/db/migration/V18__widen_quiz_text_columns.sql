-- Entity/validation limits are 255 (answers, correct options) and 250 (description);
-- earlier migrations created these as VARCHAR(225).
ALTER TABLE question_set
    ALTER COLUMN answer1 TYPE VARCHAR(255),
    ALTER COLUMN answer2 TYPE VARCHAR(255),
    ALTER COLUMN answer3 TYPE VARCHAR(255),
    ALTER COLUMN answer4 TYPE VARCHAR(255),
    ALTER COLUMN answer5 TYPE VARCHAR(255),
    ALTER COLUMN answer6 TYPE VARCHAR(255),
    ALTER COLUMN answer7 TYPE VARCHAR(255),
    ALTER COLUMN answer8 TYPE VARCHAR(255),
    ALTER COLUMN answer9 TYPE VARCHAR(255),
    ALTER COLUMN answer10 TYPE VARCHAR(255),
    ALTER COLUMN correctoptions TYPE VARCHAR(255);

ALTER TABLE challenge ALTER COLUMN description TYPE VARCHAR(250);
