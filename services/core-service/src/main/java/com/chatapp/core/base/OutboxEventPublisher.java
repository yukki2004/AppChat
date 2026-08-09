package com.chatapp.core.base;

import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.chatapp.core.base.entity.OutboxEventEntity;
import com.chatapp.core.base.repository.OutboxEventRepository;

import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import tools.jackson.databind.ObjectMapper;

@Component
@RequiredArgsConstructor
public class OutboxEventPublisher {

    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;
    private final OutboxDispatcher outboxDispatcher;


    @SneakyThrows
    public void publish(String exchange, String routingKey, UUID aggregateId, String aggregateType, Object payload) {
        String payloadJson = objectMapper.writeValueAsString(payload);
        OutboxEventEntity event = new OutboxEventEntity(routingKey, aggregateId, aggregateType, payloadJson, exchange, routingKey);
        outboxEventRepository.save(event);

        UUID outboxEventId = event.getId();
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            outboxDispatcher.attemptPublish(outboxEventId);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                outboxDispatcher.attemptPublish(outboxEventId);
            }
        });
    }
}
