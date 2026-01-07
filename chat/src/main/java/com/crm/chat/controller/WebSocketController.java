package com.crm.chat.controller;

import com.crm.chat.dto.MessageDTO;
import com.crm.chat.entity.Message;
import com.crm.chat.entity.User;
import com.crm.chat.service.MessageService;
import com.crm.chat.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.util.Map;

@Controller
@RequiredArgsConstructor
public class WebSocketController {

    private final SimpMessagingTemplate messagingTemplate;
    private final MessageService messageService;
    private final UserService userService;

    // Handle direct messages (one-to-one)
    @MessageMapping("/chat.sendDirectMessage")
    public void sendDirectMessage(@Payload Map<String, Object> messageData, Principal principal) {
        try {
            if (principal == null) {
                System.err.println("Principal is null in sendDirectMessage");
                return;
            }

            String username = principal.getName();
            User sender = userService.findByUsername(username)
                    .orElseThrow(() -> new RuntimeException("User not found"));

            Long conversationId = Long.valueOf(messageData.get("conversationId").toString());
            String content = messageData.get("content").toString();

            // Save message to database
            Message message = messageService.sendDirectMessage(sender.getId(), conversationId, content);
            MessageDTO messageDTO = MessageDTO.fromEntity(message);

            // Explicitly cast destination to String to resolve ambiguity
            String destination = "/topic/conversation." + conversationId;
            messagingTemplate.convertAndSend(destination, (Object) messageDTO);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // Handle group messages
    @MessageMapping("/chat.sendGroupMessage")
    public void sendGroupMessage(@Payload Map<String, Object> messageData, Principal principal) {
        try {
            if (principal == null) {
                System.err.println("Principal is null in sendGroupMessage");
                return;
            }

            String username = principal.getName();
            User sender = userService.findByUsername(username)
                    .orElseThrow(() -> new RuntimeException("User not found"));

            Long chatRoomId = Long.valueOf(messageData.get("chatRoomId").toString());
            String content = messageData.get("content").toString();

            // Save message to database
            Message message = messageService.sendGroupMessage(sender.getId(), chatRoomId, content);
            MessageDTO messageDTO = MessageDTO.fromEntity(message);

            // Explicitly cast destination to String to resolve ambiguity
            String destination = "/topic/chatroom." + chatRoomId;
            messagingTemplate.convertAndSend(destination, (Object) messageDTO);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // Handle user connection (when user opens chat)
    @MessageMapping("/chat.addUser")
    public void addUser(@Payload Map<String, Object> userData,
                        SimpMessageHeaderAccessor headerAccessor,
                        Principal principal) {
        try {
            String username;
            User user;

            // Check if principal is null (user not authenticated via WebSocket)
            if (principal == null) {
                // Try to get username from the payload
                username = (String) userData.get("username");
                if (username == null || username.isEmpty()) {
                    System.err.println("Principal is null and no username in payload");
                    return;
                }

                user = userService.findByUsername(username)
                        .orElseThrow(() -> new RuntimeException("User not found: " + username));
            } else {
                // Principal is available
                username = principal.getName();
                user = userService.findByUsername(username)
                        .orElseThrow(() -> new RuntimeException("User not found"));
            }

            // Set user as online
            userService.setUserOnline(user.getId());

            // Store username in websocket session
            headerAccessor.getSessionAttributes().put("username", username);

            // Notify all users about new user online
            Map<String, Object> notification = Map.of(
                    "type", "USER_ONLINE",
                    "userId", user.getId(),
                    "username", user.getUsername(),
                    "fullName", user.getFullName()
            );

            // Explicitly cast destination to String to resolve ambiguity
            String destination = "/topic/public";
            messagingTemplate.convertAndSend(destination, (Object) notification);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // Handle typing indicator
    @MessageMapping("/chat.typing")
    public void handleTyping(@Payload Map<String, Object> typingData, Principal principal) {
        try {
            if (principal == null) {
                System.err.println("Principal is null in handleTyping");
                return;
            }

            String username = principal.getName();
            User user = userService.findByUsername(username)
                    .orElseThrow(() -> new RuntimeException("User not found"));

            String chatType = typingData.get("chatType").toString(); // "conversation" or "chatroom"
            Long chatId = Long.valueOf(typingData.get("chatId").toString());
            Boolean isTyping = Boolean.valueOf(typingData.get("isTyping").toString());

            Map<String, Object> notification = Map.of(
                    "userId", user.getId(),
                    "username", user.getUsername(),
                    "fullName", user.getFullName(),
                    "isTyping", isTyping
            );

            String destination;
            if ("conversation".equals(chatType)) {
                destination = "/topic/conversation." + chatId + ".typing";
            } else if ("chatroom".equals(chatType)) {
                destination = "/topic/chatroom." + chatId + ".typing";
            } else {
                return; // Invalid chat type
            }

            // Explicitly cast to resolve ambiguity
            messagingTemplate.convertAndSend(destination, (Object) notification);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // Handle message read receipt
    @MessageMapping("/chat.markAsRead")
    public void markAsRead(@Payload Map<String, Object> readData, Principal principal) {
        try {
            Long messageId = Long.valueOf(readData.get("messageId").toString());
            messageService.markMessageAsRead(messageId);

            // Optionally notify sender that message was read
            Message message = messageService.findById(messageId).orElse(null);
            if (message != null && message.getReadAt() != null) {
                Map<String, Object> notification = Map.of(
                        "messageId", messageId,
                        "readAt", message.getReadAt().toString()
                );

                if (message.isDirectMessage()) {
                    String destination = "/topic/conversation." + message.getConversation().getId() + ".read";
                    messagingTemplate.convertAndSend(destination, (Object) notification);
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}