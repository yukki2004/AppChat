package com.chatapp.core.base.constant;

/** Stored in DB as a fixed hand-assigned code (see skills/naming-conventions.md #3), never as
 *  ordinal — application code and JSON always use the name. */
public enum FriendshipStatus {
    PENDING(1),
    ACCEPTED(2),
    REJECTED(3),
    CANCELLED(4);

    private final int code;

    FriendshipStatus(int code) {
        this.code = code;
    }

    public int getCode() {
        return code;
    }

    public static FriendshipStatus fromCode(int code) {
        for (FriendshipStatus status : values()) {
            if (status.code == code) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown FriendshipStatus code: " + code);
    }
}
