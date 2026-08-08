package com.chatapp.core.base.constant;

/** What the block mutes: {@code MESSAGE}/{@code CALL} alone, or {@code ALL} (both). v1 only ever
 *  inserts {@code ALL} — plain "Block" always mutes both. Picking {@code MESSAGE} or {@code CALL}
 *  alone is a future UI choice (not built yet). Stored as plain text (see
 *  {@code UserBlockEntity#scope}) — no numeric code needed. */
public enum BlockScope {
    MESSAGE,
    CALL,
    ALL
}
