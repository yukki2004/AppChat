package com.chatapp.core.group.dto.response;

/** Shared by every path that can add someone to a group (join-by-token, direct add-member) —
 *  whether the group's {@code require_approval} routed them straight into {@code group_members}
 *  or into a pending {@code group_join_requests} row instead. */
public enum GroupJoinOutcome {
    JOINED,
    PENDING_APPROVAL
}
