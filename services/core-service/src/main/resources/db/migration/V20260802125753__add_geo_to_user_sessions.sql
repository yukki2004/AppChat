-- E.10 (docs/.../system/05-cookie-auth-flow.md): resolved offline from ip_address via a
-- self-hosted MaxMind GeoLite2-City .mmdb lookup, city/country-level accuracy only.
ALTER TABLE user_sessions ADD COLUMN login_country VARCHAR(2);
ALTER TABLE user_sessions ADD COLUMN login_city VARCHAR(100);
