package com.aldeamo.messaging.workerconsumer.controller;

import com.aldeamo.messaging.workerconsumer.document.MessageDocument;
import com.aldeamo.messaging.workerconsumer.service.MessageProcessingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/messages")
@RequiredArgsConstructor
@Slf4j
public class MessageQueryController {

    private final MessageProcessingService messageProcessingService;

    @GetMapping("/{destination}")
    public ResponseEntity<List<MessageDocument>> getMessagesByDestination(@PathVariable String destination) {
        log.debug("GET /messages/{}", destination);
        List<MessageDocument> messages = messageProcessingService.getMessagesByDestination(destination);
        return ResponseEntity.ok(messages);
    }
}
