package com.chatapp.core.base.constant;

/**
 * Every RabbitMQ exchange/routing key Core Service publishes to — 2 flat constants per event
 * (`_EXCHANGE`, `_ROUTING_KEY`), passed directly to `base/OutboxEventPublisher#publish` (see
 * `skills/outbox-pattern.md`), so neither is ever hand-typed or duplicated at the call site.
 *
 * Deliberately has NO `_QUEUE` constants — declaring/binding a queue is the CONSUMER's job, not
 * the publisher's (each consumer picks its own queue name/durability/ack mode and is the one
 * that actually needs the queue to exist). Core Service publishing here has no business knowing
 * or pre-declaring another service's internal queue name.
 */
public final class RabbitConstant {

    public static final class UserExchange {
        public static final String NAME = "user.exchange";

        public static final String USER_REGISTERED_EXCHANGE = NAME;
        public static final String USER_REGISTERED_ROUTING_KEY = "user.registered";

        public static final String USER_PROFILE_UPDATED_EXCHANGE = NAME;
        public static final String USER_PROFILE_UPDATED_ROUTING_KEY = "user.profile_updated";

        public static final String USER_BLOCKED_EXCHANGE = NAME;
        public static final String USER_BLOCKED_ROUTING_KEY = "user.blocked";

        public static final String USER_NEW_DEVICE_LOGIN_EXCHANGE = NAME;
        public static final String USER_NEW_DEVICE_LOGIN_ROUTING_KEY = "user.new_device_login";

        public static final String USER_LOGGED_OUT_ALL_EXCHANGE = NAME;
        public static final String USER_LOGGED_OUT_ALL_ROUTING_KEY = "user.logged_out_all";

        public static final String USER_QR_LOGIN_APPROVED_EXCHANGE = NAME;
        public static final String USER_QR_LOGIN_APPROVED_ROUTING_KEY = "user.qr_login_approved";

        public static final String USER_BLOCK_SET_EXCHANGE = NAME;
        public static final String USER_BLOCK_SET_ROUTING_KEY = "user.block_set";

        public static final String USER_BLOCK_REMOVED_EXCHANGE = NAME;
        public static final String USER_BLOCK_REMOVED_ROUTING_KEY = "user.block_removed";

        public static final String FRIEND_REQUEST_SENT_EXCHANGE = NAME;
        public static final String FRIEND_REQUEST_SENT_ROUTING_KEY = "friend.request_sent";

        public static final String FRIEND_ACCEPTED_EXCHANGE = NAME;
        public static final String FRIEND_ACCEPTED_ROUTING_KEY = "friend.accepted";

        public static final String FRIEND_REMOVED_EXCHANGE = NAME;
        public static final String FRIEND_REMOVED_ROUTING_KEY = "friend.removed";

        private UserExchange() {
        }
    }

    public static final class GroupExchange {
        public static final String NAME = "group.exchange";

        public static final String GROUP_MEMBER_JOINED_EXCHANGE = NAME;
        public static final String GROUP_MEMBER_JOINED_ROUTING_KEY = "group.member_joined";

        public static final String GROUP_MEMBER_REMOVED_EXCHANGE = NAME;
        public static final String GROUP_MEMBER_REMOVED_ROUTING_KEY = "group.member_removed";

        public static final String GROUP_ROLE_CHANGED_EXCHANGE = NAME;
        public static final String GROUP_ROLE_CHANGED_ROUTING_KEY = "group.role_changed";

        public static final String GROUP_JOIN_REQUEST_EXCHANGE = NAME;
        public static final String GROUP_JOIN_REQUEST_ROUTING_KEY = "group.join_request";

        public static final String GROUP_DELETED_EXCHANGE = NAME;
        public static final String GROUP_DELETED_ROUTING_KEY = "group.deleted";

        public static final String GROUP_EVENT_CREATED_EXCHANGE = NAME;
        public static final String GROUP_EVENT_CREATED_ROUTING_KEY = "group.event_created";

        public static final String GROUP_EVENT_REMINDER_EXCHANGE = NAME;
        public static final String GROUP_EVENT_REMINDER_ROUTING_KEY = "group.event_reminder";

        private GroupExchange() {
        }
    }

    private RabbitConstant() {
    }
}
