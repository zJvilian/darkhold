-- Accounts signed in through an OpenID Connect provider are matched by issuer registration + subject
ALTER TABLE member ADD COLUMN IF NOT EXISTS oidc_provider VARCHAR(64);
ALTER TABLE member ADD COLUMN IF NOT EXISTS oidc_subject VARCHAR(255);
CREATE UNIQUE INDEX IF NOT EXISTS idx_member_oidc_identity ON member (oidc_provider, oidc_subject)
    WHERE oidc_provider IS NOT NULL AND oidc_subject IS NOT NULL;
