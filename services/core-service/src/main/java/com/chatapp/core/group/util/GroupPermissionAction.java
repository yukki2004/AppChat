package com.chatapp.core.group.util;

/** Actions gated by {@link GroupPermissionResolver} — kept in 1 enum so every caller (service
 *  method, future gRPC {@code CheckGroupRole}) checks against the same fixed set. */
public enum GroupPermissionAction {
    CAN_APPROVE_MEMBERS,
    CAN_KICK_MEMBERS,
    CAN_EDIT_GROUP_INFO,
    CAN_MANAGE_EVENTS
}
