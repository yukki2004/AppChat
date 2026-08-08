-- Lets logoutSession (DELETE /auth/sessions/{sessionId}) blacklist the access_token of the
-- OTHER device being revoked immediately, instead of waiting up to 15 min for it to expire on
-- its own — see docs/.../system/05-cookie-auth-flow.md E.6. NULL for rows created before this
-- migration (their access_token, if still live, just expires naturally as before).
ALTER TABLE user_sessions ADD COLUMN access_token_jti VARCHAR(36);
