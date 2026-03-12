package com.aldeamo.messaging.workerconsumer.service;

import com.aldeamo.messaging.workerconsumer.document.MessageDocument;
import com.aldeamo.messaging.workerconsumer.repository.MessageRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class MessageProcessingService {

    private final MessageRepository messageRepository;
    private final int maxMessagesPerDestination;
    private final int timeWindowHours;
    private final Counter messagesProcessedCounter;
    private final Counter messagesRateLimitedCounter;
    private final Timer processingTimer;

    public MessageProcessingService(MessageRepository messageRepository,
                                     @Value("${app.rules.max-messages-per-destination}") int maxMessagesPerDestination,
                                     @Value("${app.rules.time-window-hours}") int timeWindowHours,
                                     MeterRegistry meterRegistry) {
        this.messageRepository = messageRepository;
        this.maxMessagesPerDestination = maxMessagesPerDestination;
        this.timeWindowHours = timeWindowHours;
        this.messagesProcessedCounter = Counter.builder("messages.processed")
                .description("Total messages processed successfully")
                .register(meterRegistry);
        this.messagesRateLimitedCounter = Counter.builder("messages.rate_limited")
                .description("Total messages rejected by rate limit")
                .register(meterRegistry);
        this.processingTimer = Timer.builder("messages.processing.time")
                .description("Message processing time")
                .register(meterRegistry);
    }

    public void processMessage(Map<String, Object> payload, long timestampHeader) {
        long processingTime = System.currentTimeMillis() - timestampHeader;

        String origin = (String) payload.get("origin");
        String destination = (String) payload.get("destination");
        String messageType = (String) payload.get("messageType");
        String content = (String) payload.get("content");

        log.info("Processing message: origin={}, destination={}, type={}, processingTime={}ms",
                origin, destination, messageType, processingTime);

        processingTimer.record(() -> {
            LocalDateTime timeWindowStart = LocalDateTime.now().minusHours(timeWindowHours);
            long messageCount = messageRepository.countByDestinationAndCreatedDateAfterAndErrorIsNull(
                    destination, timeWindowStart);

            MessageDocument document = MessageDocument.builder()
                    .origin(origin)
                    .destination(destination)
                    .messageType(messageType)
                    .content(content)
                    .processingTime(processingTime)
                    .createdDate(LocalDateTime.now())
                    .build();

            if (messageCount >= maxMessagesPerDestination) {
                String errorMsg = String.format(
                        "Rate limit exceeded: destination '%s' has received %d messages in the last %d hours (max: %d)",
                        destination, messageCount, timeWindowHours, maxMessagesPerDestination);
                document.setError(errorMsg);
                messagesRateLimitedCounter.increment();
                log.warn(errorMsg);
            } else {
                messagesProcessedCounter.increment();
            }

            messageRepository.save(document);
            log.info("Message persisted to MongoDB. ID: {}, hasError: {}", document.getId(), document.getError() != null);
        });
    }

    public List<MessageDocument> getMessagesByDestination(String destination) {
        log.debug("Querying messages for destination: {}", destination);
        return messageRepository.findByDestination(destination);
    }
}
