package com.chatapp.core.base.constant;

/** Stored as the plain enum name (VARCHAR(30)), not a hand-assigned code like {@link OtpPurpose}
 *  — this one is read directly by admins/security tooling querying `login_audit_logs`, so an
 *  opaque numeric code would just add a lookup step with no benefit. */
public enum LoginAuditEventType {
    LOGIN_SUCCESS,
    LOGIN_FAILED,
    LOGOUT,
    PASSWORD_CHANGE,
    SESSION_REVOKE
}
