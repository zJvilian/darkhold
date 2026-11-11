-- Game ownership used to be keyed by first name alone, which is not unique.
-- It is now "<first name>#<user id>"; rewrite existing rows where the owner is unambiguous.
UPDATE game g SET moderator = m.firstname || '#' || m.id FROM member m
 WHERE g.moderator = m.firstname AND (SELECT count(*) FROM member x WHERE x.firstname = m.firstname) = 1;
UPDATE game_result r SET moderator = m.firstname || '#' || m.id FROM member m
 WHERE r.moderator = m.firstname AND (SELECT count(*) FROM member x WHERE x.firstname = m.firstname) = 1;
UPDATE current_game_session s SET moderator = m.firstname || '#' || m.id FROM member m
 WHERE s.moderator = m.firstname AND (SELECT count(*) FROM member x WHERE x.firstname = m.firstname) = 1;
