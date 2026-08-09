package com.chatapp.core.base;

import java.util.UUID;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.chatapp.core.base.constant.OutboxEventStatus;
import com.chatapp.core.base.entity.OutboxEventEntity;
import com.chatapp.core.base.repository.OutboxEventRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
public class OutboxDispatcher {

    private final OutboxEventRepository outboxEventRepository;
    private final RabbitTemplate rabbitTemplate;

    @Transactional
    public void attemptPublish(UUID outboxEventId) {
        outboxEventRepository.findById(outboxEventId).ifPresent(this::attemptPublish);
    }

    private void attemptPublish(OutboxEventEntity event) {
        if (event.getStatus() != OutboxEventStatus.PENDING) {
            return; 
        }
        try {
            rabbitTemplate.convertAndSend(event.getExchange(), event.getRoutingKey(), event.getPayload(), message -> {
                message.getMessageProperties().setMessageId(event.getEventId().toString());
                message.getMessageProperties().setContentType("application/json");
                return message;
            });
            event.markPublished();
            log.debug("OutboxDispatcher: published id={} eventId={} routingKey={}",
                    event.getId(), event.getEventId(), event.getRoutingKey());
        } catch (Exception e) {
            event.markFailed(e.getMessage());
            log.warn("OutboxDispatcher: publish failed id={} eventId={} exchange={} routingKey={}: {}",
                    event.getId(), event.getEventId(), event.getExchange(), event.getRoutingKey(), e.getMessage());
        }
    }
}
