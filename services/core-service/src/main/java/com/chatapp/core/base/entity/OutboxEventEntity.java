package com.chatapp.core.base.entity;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.chatapp.core.base.constant.OutboxEventStatus;

/**
 * Backs skills/outbox-pattern.md — inserted in the SAME transaction as the business write it
 * accompanies, never published directly from request-handling code. A separate Go relay worker
 * (services/core-service/outbox-relay/) polls {@code status=PENDING} rows and publishes them.
 */
@Entity
@Table(name = "outbox_events")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OutboxEventEntity {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "event_id", nullable = false, unique = true)
    private UUID eventId;

    @Column(name = "event_type", nullable = false)
    private String eventType;

    @Column(name = "aggregate_id", nullable = false)
    private UUID aggregateId;

    @Column(name = "aggregate_type", nullable = false)
    private String aggregateType;

    /** Pre-serialized JSON string — the relay worker republishes this verbatim as the RabbitMQ
     *  message body, so this entity never needs to deserialize it back into a Java type. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", nullable = false)
    private String payload;

    @Column(name = "exchange", nullable = false)
    private String exchange;

    @Column(name = "routing_key", nullable = false)
    private String routingKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private OutboxEventStatus status;

    @Column(name = "retry_count", nullable = false)
    private int retryCount;

    /** Set by the Go relay worker on a failed publish attempt — never written from Java, the
     *  business transaction that inserts this row always starts with status=PENDING/no error. */
    @Column(name = "last_error")
    private String lastError;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "published_at")
    private Instant publishedAt;

    private static final int MAX_RETRIES = 5;

    public OutboxEventEntity(String eventType, UUID aggregateId, String aggregateType,
                              String payload, String exchange, String routingKey) {
        this.eventId = UUID.randomUUID();
        this.eventType = eventType;
        this.aggregateId = aggregateId;
        this.aggregateType = aggregateType;
        this.payload = payload;
        this.exchange = exchange;
        this.routingKey = routingKey;
        this.status = OutboxEventStatus.PENDING;
        this.retryCount = 0;
        this.createdAt = Instant.now();
    }

    public void markPublished() {
        this.status = OutboxEventStatus.PUBLISHED;
        this.publishedAt = Instant.now();
    }

    /** Stays PENDING until MAX_RETRIES is reached, then gives up (FAILED) — see
     *  OutboxDispatcher/OutboxRetryJob, the only callers. */
    public void markFailed(String error) {
        this.retryCount++;
        this.lastError = error;
        if (this.retryCount >= MAX_RETRIES) {
            this.status = OutboxEventStatus.FAILED;
        }
    }
}
