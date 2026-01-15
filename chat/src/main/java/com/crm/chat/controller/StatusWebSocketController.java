package com.crm.chat.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.util.Map;

/**
 * WebSocket controller for broadcasting user status changes
 */
@Controller
@RequiredArgsConstructor
public class StatusWebSocketController {

    private final SimpMessagingTemplate messagingTemplate;

    /**
     * Handle status update messages from clients
     * Receives message from /app/user.status.update
     * Broadcasts to /topic/user.status
     */
    @MessageMapping("/user.status.update")
    public void handleStatusUpdate(Map<String, Object> statusUpdate) {
        // Broadcast the status update to all connected clients
        messagingTemplate.convertAndSend("/topic/user.status", (Object) statusUpdate);
    }
}