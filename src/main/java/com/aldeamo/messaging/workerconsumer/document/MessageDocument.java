package com.aldeamo.messaging.workerconsumer.document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Document(collection = "messages")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MessageDocument {

    @Id
    private String id;

    private String origin;

    @Indexed
    private String destination;

    private String messageType;

    private String content;

    private Long processingTime;

    private LocalDateTime createdDate;

    private String error;
}
