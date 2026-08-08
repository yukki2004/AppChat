package com.chatapp.core.base.constant;

/** Stored as VARCHAR(20) in {@code user_oauth_providers.provider} (already the decision recorded
 *  in docs/.../03-core-service.md 3.4 — not a fresh enum-storage choice, so the
 *  hand-assigned-SMALLINT rule in skills/naming-conventions.md #3 doesn't apply here). */
public enum OAuthProvider {
    GOOGLE,
    FACEBOOK,
    APPLE
}
