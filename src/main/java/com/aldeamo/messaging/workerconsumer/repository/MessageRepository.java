package com.aldeamo.messaging.workerconsumer.repository;

import com.aldeamo.messaging.workerconsumer.document.MessageDocument;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface MessageRepository extends MongoRepository<MessageDocument, String> {

    List<MessageDocument> findByDestination(String destination);

    long countByDestinationAndCreatedDateAfterAndErrorIsNull(String destination, LocalDateTime after);
}
