package com.chatapp.core.base.constant;

/** Stored in DB as a fixed hand-assigned code (see skills/naming-conventions.md #3), never as
 *  ordinal — application code and JSON always use the name. */
public enum OtpPurpose {
    REGISTER(1),
    LOGIN_2FA(2),
    RESET_PASSWORD(3),
    CHANGE_EMAIL(4),
    ENABLE_2FA(5),
    /** Adding email/phone to an account that registered with only the other one — a separate
     *  flow from CHANGE_EMAIL (that one replaces an existing verified email via pending_email;
     *  this one fills in a currently-NULL column). See conversation decision on link flow. */
    LINK_IDENTIFIER(6);

    private final int code;

    OtpPurpose(int code) {
        this.code = code;
    }

    public int getCode() {
        return code;
    }

    public static OtpPurpose fromCode(int code) {
        for (OtpPurpose purpose : values()) {
            if (purpose.code == code) {
                return purpose;
            }
        }
        throw new IllegalArgumentException("Unknown OtpPurpose code: " + code);
    }
}
