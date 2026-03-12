package com.aldeamo.messaging.workerconsumer.consumer;

import com.aldeamo.messaging.workerconsumer.service.MessageProcessingService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
@Slf4j
@RequiredArgsConstructor
public class MessageConsumer {

    private final MessageProcessingService messageProcessingService;
    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;

    @Value("${app.rabbitmq.exchange}")
    private String exchange;

    @Value("${app.rabbitmq.dlq-routing-key}")
    private String dlqRoutingKey;

    @Value("${app.rabbitmq.retry-routing-key}")
    private String retryRoutingKey;

    @Value("${app.rabbitmq.max-retries}")
    private int maxRetries;

    @SuppressWarnings("unchecked")
    @RabbitListener(queues = "${app.rabbitmq.queue}")
    public void consumeMessage(Message message, Channel channel) throws Exception {
        long deliveryTag = message.getMessageProperties().getDeliveryTag();

        try {
            Map<String, Object> headers = message.getMessageProperties().getHeaders();
            Long timestamp = getTimestampFromHeaders(headers);

            Map<String, Object> payload = objectMapper.readValue(message.getBody(), Map.class);

            log.info("Received message from queue: origin={}, destination={}",
                    payload.get("origin"), payload.get("destination"));

            messageProcessingService.processMessage(payload, timestamp);

            channel.basicAck(deliveryTag, false);
            log.debug("Message acknowledged successfully");

        } catch (Exception ex) {
            log.error("Error processing message: {}", ex.getMessage(), ex);
            handleFailedMessage(message, channel, deliveryTag, ex);
        }
    }

    private Long getTimestampFromHeaders(Map<String, Object> headers) {
        Object ts = headers.get("timestamp");
        if (ts instanceof Long l) {
            return l;
        }
        if (ts instanceof Integer i) {
            return i.longValue();
        }
        if (ts instanceof String s) {
            return Long.parseLong(s);
        }
        log.warn("Timestamp header not found or invalid, using current time");
        return System.currentTimeMillis();
    }

    private void handleFailedMessage(Message message, Channel channel, long deliveryTag, Exception ex) throws Exception {
        int retryCount = getRetryCount(message.getMessageProperties());

        if (retryCount < maxRetries) {
            log.warn("Retrying message (attempt {}/{})", retryCount + 1, maxRetries);
            message.getMessageProperties().setHeader("x-retry-count", retryCount + 1);
            rabbitTemplate.send(exchange, retryRoutingKey, message);
            channel.basicAck(deliveryTag, false);
        } else {
            log.error("Max retries ({}) exceeded. Sending message to DLQ", maxRetries);
            message.getMessageProperties().setHeader("x-dlq-reason", ex.getMessage());
            message.getMessageProperties().setHeader("x-dlq-timestamp", System.currentTimeMillis());
            rabbitTemplate.send(exchange, dlqRoutingKey, message);
            channel.basicAck(deliveryTag, false);
        }
    }

    private int getRetryCount(MessageProperties properties) {
        Object retryCount = properties.getHeader("x-retry-count");
        if (retryCount instanceof Integer i) {
            return i;
        }
        List<Map<String, ?>> xDeathHeader = properties.getHeader("x-death");
        if (xDeathHeader != null && !xDeathHeader.isEmpty()) {
            Object count = xDeathHeader.getFirst().get("count");
            if (count instanceof Long l) {
                return l.intValue();
            }
        }
        return 0;
    }
}
