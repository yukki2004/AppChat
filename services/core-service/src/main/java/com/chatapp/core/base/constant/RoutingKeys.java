package com.chatapp.core.base.constant;

/**
 * RabbitMQ exchange names + routing keys Core Service publishes to — no outbox/publisher code
 * exists yet (see `skills/outbox-pattern.md` before wiring this up), this is just the constants
 * ready for when it does, so routing keys are never hand-typed at the call site.
 */
public final class RoutingKeys {

    public static final class UserExchange {
        public static final String NAME = "user.exchange";

        public static final String USER_REGISTERED = "user.registered";
        public static final String USER_PROFILE_UPDATED = "user.profile_updated";
        public static final String USER_BLOCKED = "user.blocked";
        public static final String USER_NEW_DEVICE_LOGIN = "user.new_device_login";
        public static final String USER_LOGGED_OUT_ALL = "user.logged_out_all";
        public static final String USER_BLOCK_SET = "user.block_set";
        public static final String USER_BLOCK_REMOVED = "user.block_removed";
        public static final String FRIEND_REQUEST_SENT = "friend.request_sent";
        public static final String FRIEND_ACCEPTED = "friend.accepted";
        public static final String FRIEND_REMOVED = "friend.removed";

        private UserExchange() {
        }
    }

    public static final class GroupExchange {
        public static final String NAME = "group.exchange";

        public static final String GROUP_MEMBER_JOINED = "group.member_joined";
        public static final String GROUP_MEMBER_REMOVED = "group.member_removed";
        public static final String GROUP_ROLE_CHANGED = "group.role_changed";
        public static final String GROUP_JOIN_REQUEST = "group.join_request";
        public static final String GROUP_DELETED = "group.deleted";
        public static final String GROUP_EVENT_CREATED = "group.event_created";
        public static final String GROUP_EVENT_REMINDER = "group.event_reminder";

        private GroupExchange() {
        }
    }

    private RoutingKeys() {
    }
}
