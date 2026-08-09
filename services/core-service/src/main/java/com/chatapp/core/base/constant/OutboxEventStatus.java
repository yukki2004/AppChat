package com.chatapp.core.base.constant;

/** Stored as the plain enum name (VARCHAR(20), see skills/outbox-pattern.md) — the relay worker
 *  is a separate Go process querying this column directly by string, not through this enum, so
 *  an opaque numeric code would just add a translation step on that side for no benefit. */
public enum OutboxEventStatus {
    PENDING,
    PUBLISHED,
    FAILED
}
