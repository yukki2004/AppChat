package com.chatapp.core.scheduler;

import java.util.List;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.chatapp.core.base.OutboxDispatcher;
import com.chatapp.core.base.constant.OutboxEventStatus;
import com.chatapp.core.base.entity.OutboxEventEntity;
import com.chatapp.core.base.repository.OutboxEventRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Fallback for the outbox pattern's immediate after-commit publish (base/OutboxEventPublisher ->
 * base/OutboxDispatcher) — sweeps whatever stayed PENDING (process crashed between commit and
 * publish, RabbitMQ was briefly unreachable, ...). Runs as a plain {@code @Scheduled} task
 * inside core-service itself, not a separate process — see conversation decision: no separate
 * Go relay, this service owns its own outbox end to end (skills/outbox-pattern.md).
 *
 * Not safe against double-publish if core-service itself ever runs as >1 replica (no
 * SELECT ... FOR UPDATE SKIP LOCKED / distributed lock here) — acceptable for now at the
 * project's current single-instance scale; revisit (e.g. ShedLock) before scaling this service
 * horizontally.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OutboxRetryJob {

    private final OutboxEventRepository outboxEventRepository;
    private final OutboxDispatcher outboxDispatcher;

    @Scheduled(fixedDelayString = "${app.outbox.retry-interval-ms}")
    public void retryPending() {
        List<OutboxEventEntity> pending = outboxEventRepository.findTop100ByStatusOrderByCreatedAtAsc(OutboxEventStatus.PENDING);
        if (pending.isEmpty()) {
            return;
        }
        log.debug("OutboxRetryJob: retrying {} pending event(s)", pending.size());
        for (OutboxEventEntity event : pending) {
            outboxDispatcher.attemptPublish(event.getId());
        }
    }
}
