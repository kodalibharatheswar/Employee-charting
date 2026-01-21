package com.crm.chat.controller;

import com.crm.chat.dto.MessageDTO;
import com.crm.chat.entity.Message;
import com.crm.chat.entity.User;
import com.crm.chat.entity.Call;
import com.crm.chat.entity.Conversation;
import com.crm.chat.service.MessageService;
import com.crm.chat.service.UserService;
import com.crm.chat.service.CallService;
import com.crm.chat.service.ConversationService;

import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.util.Map;
import java.util.HashMap;

@Controller
@RequiredArgsConstructor
public class WebSocketController {

    private final SimpMessagingTemplate messagingTemplate;
    private final MessageService messageService;
    private final UserService userService;
    private final CallService callService;
    private final ConversationService conversationService; // ✅ ADDED THIS


    /**
     * Handle group messages
     */
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

            // Broadcast to chatroom subscribers
            String destination = "/topic/chatroom." + chatRoomId;
            messagingTemplate.convertAndSend(destination, (Object) messageDTO);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Handle user connection (when user opens chat)
     */
    @MessageMapping("/chat.addUser")
    public void addUser(@Payload Map<String, Object> userData,
                        SimpMessageHeaderAccessor headerAccessor,
                        Principal principal) {
        try {
            String username;
            User user;

            if (principal == null) {
                username = (String) userData.get("username");
                if (username == null || username.isEmpty()) {
                    System.err.println("❌ Principal is null and no username in payload");
                    return;
                }
                user = userService.findByUsername(username)
                        .orElseThrow(() -> new RuntimeException("User not found: " + username));
            } else {
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
                    "fullName", user.getFullName(),
                    "status", user.getStatus().name()
            );

            String destination = "/topic/public";
            messagingTemplate.convertAndSend(destination, (Object) notification);
            
            System.out.println("✅ User online: " + user.getFullName());

        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    /**
     * Handle typing indicator
     */
    @MessageMapping("/chat.typing")
    public void handleTyping(@Payload Map<String, Object> typingData, Principal principal) {
        try {
            if (principal == null) {
                System.err.println("❌ Principal is null in handleTyping");
                return;
            }

            String username = principal.getName();
            User user = userService.findByUsername(username)
                    .orElseThrow(() -> new RuntimeException("User not found"));

            String chatType = typingData.get("chatType").toString();
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
                return;
            }

            messagingTemplate.convertAndSend(destination, (Object) notification);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

   
    // ============================================================================
    // NEW WEBRTC SIGNALING METHODS FOR AUDIO/VIDEO CALLS
    // ============================================================================

    /**
     * Initiate a call (audio/video/screen share)
     * 
     * Payload structure:
     * {
     *   "callType": "AUDIO" | "VIDEO" | "SCREEN_SHARE",
     *   "callMode": "DIRECT" | "GROUP",
     *   "conversationId": Long (for direct calls),
     *   "chatRoomId": Long (for group calls),
     *   "recipientId": Long (for direct calls)
     * }
     */
    @MessageMapping("/call.initiate")
    public void initiateCall(@Payload Map<String, Object> callData, Principal principal) {
        try {
            if (principal == null) {
                System.err.println("Principal is null in initiateCall");
                return;
            }

            String username = principal.getName();
            User caller = userService.findByUsername(username)
                    .orElseThrow(() -> new RuntimeException("User not found"));

            String callTypeStr = callData.get("callType").toString();
            String callModeStr = callData.get("callMode").toString();
            
            Call.CallType callType = Call.CallType.valueOf(callTypeStr);
            Call.CallMode callMode = Call.CallMode.valueOf(callModeStr);

            Call call;

            if (callMode == Call.CallMode.DIRECT) {
                // Direct call (1-on-1)
                Long conversationId = Long.valueOf(callData.get("conversationId").toString());
                call = callService.initiateDirectCall(caller.getId(), conversationId, callType);

                // Get recipient ID and notify them
                Long recipientId = Long.valueOf(callData.get("recipientId").toString());
                
                Map<String, Object> notification = createCallNotification(call, "INCOMING_CALL", caller);
                
                // Send to specific user using /user/{userId}/queue/call
                messagingTemplate.convertAndSendToUser(
                    recipientId.toString(),
                    "/queue/call",
                    notification
                );

            } else {
                // Group call (conference)
                Long chatRoomId = Long.valueOf(callData.get("chatRoomId").toString());
                call = callService.initiateGroupCall(caller.getId(), chatRoomId, callType);

                // Notify all group members
                Map<String, Object> notification = createCallNotification(call, "INCOMING_GROUP_CALL", caller);
                
                String destination = "/topic/chatroom." + chatRoomId + ".call";
                messagingTemplate.convertAndSend((String)destination, (Object)notification);
            }

            System.out.println("Call initiated: " + call.getId() + " by " + caller.getFullName());

        } catch (Exception e) {
            e.printStackTrace();
            sendCallError(principal, "Failed to initiate call: " + e.getMessage());
        }
    }

    /**
     * WebRTC Offer - Send SDP offer to recipient(s)
     * 
     * Payload structure:
     * {
     *   "callId": Long,
     *   "offer": { sdp, type }, // WebRTC SDP offer
     *   "recipientId": Long (for direct calls)
     * }
     */
    @MessageMapping("/call.offer")
    public void handleOffer(@Payload Map<String, Object> offerData, Principal principal) {
        try {
            if (principal == null) return;

            String username = principal.getName();
            User sender = userService.findByUsername(username).orElseThrow();

            Long callId = Long.valueOf(offerData.get("callId").toString());
            Map<String, Object> offer = (Map<String, Object>) offerData.get("offer");

            Call call = callService.findById(callId)
                    .orElseThrow(() -> new RuntimeException("Call not found"));

            Map<String, Object> message = new HashMap<>();
            message.put("type", "OFFER");
            message.put("callId", callId);
            message.put("offer", offer);
            message.put("senderId", sender.getId());
            message.put("senderName", sender.getFullName());

            if (call.isDirectCall()) {
                // Direct call - send to specific recipient
                Long recipientId = Long.valueOf(offerData.get("recipientId").toString());
                messagingTemplate.convertAndSendToUser(
                    recipientId.toString(),
                    "/queue/call.signal",
                    message
                );
            } else {
                // Group call - broadcast to all participants
                String destination = "/topic/chatroom." + call.getChatRoom().getId() + ".signal";
                messagingTemplate.convertAndSend((String)destination, (Object)message);
            }

        } catch (Exception e) {
            e.printStackTrace();
            sendCallError(principal, "Failed to send offer: " + e.getMessage());
        }
    }

    /**
     * WebRTC Answer - Send SDP answer back to caller
     * 
     * Payload structure:
     * {
     *   "callId": Long,
     *   "answer": { sdp, type }, // WebRTC SDP answer
     *   "recipientId": Long // The caller's ID
     * }
     */
    @MessageMapping("/call.answer")
    public void handleAnswer(@Payload Map<String, Object> answerData, Principal principal) {
        try {
            if (principal == null) return;

            String username = principal.getName();
            User sender = userService.findByUsername(username).orElseThrow();

            Long callId = Long.valueOf(answerData.get("callId").toString());
            Map<String, Object> answer = (Map<String, Object>) answerData.get("answer");
            Long recipientId = Long.valueOf(answerData.get("recipientId").toString());

            // Update call status to ONGOING
            callService.changeCallStatus(callId, Call.CallStatus.ONGOING);

            Map<String, Object> message = new HashMap<>();
            message.put("type", "ANSWER");
            message.put("callId", callId);
            message.put("answer", answer);
            message.put("senderId", sender.getId());
            message.put("senderName", sender.getFullName());

            // Send answer to the caller
            messagingTemplate.convertAndSendToUser(
                recipientId.toString(),
                "/queue/call.signal",
                message
            );

        } catch (Exception e) {
            e.printStackTrace();
            sendCallError(principal, "Failed to send answer: " + e.getMessage());
        }
    }

    /**
     * WebRTC ICE Candidate - Exchange ICE candidates for NAT traversal
     * 
     * Payload structure:
     * {
     *   "callId": Long,
     *   "candidate": { candidate, sdpMid, sdpMLineIndex }, // ICE candidate
     *   "recipientId": Long
     * }
     */
    @MessageMapping("/call.ice-candidate")
    public void handleIceCandidate(@Payload Map<String, Object> candidateData, Principal principal) {
        try {
            if (principal == null) return;

            String username = principal.getName();
            User sender = userService.findByUsername(username).orElseThrow();

            Long callId = Long.valueOf(candidateData.get("callId").toString());
            Map<String, Object> candidate = (Map<String, Object>) candidateData.get("candidate");
            
            Call call = callService.findById(callId)
                    .orElseThrow(() -> new RuntimeException("Call not found"));

            Map<String, Object> message = new HashMap<>();
            message.put("type", "ICE_CANDIDATE");
            message.put("callId", callId);
            message.put("candidate", candidate);
            message.put("senderId", sender.getId());

            if (call.isDirectCall()) {
                // Direct call - send to specific recipient
                Long recipientId = Long.valueOf(candidateData.get("recipientId").toString());
                messagingTemplate.convertAndSendToUser(
                    recipientId.toString(),
                    "/queue/call.signal",
                    message
                );
            } else {
                // Group call - broadcast to all participants except sender
                String destination = "/topic/chatroom." + call.getChatRoom().getId() + ".signal";
                messagingTemplate.convertAndSend((String)destination, (Object)message);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Join an ongoing call
     * 
     * Payload structure:
     * {
     *   "callId": Long
     * }
     */
    @MessageMapping("/call.join")
    public void joinCall(@Payload Map<String, Object> joinData, Principal principal) {
        try {
            if (principal == null) return;

            String username = principal.getName();
            User user = userService.findByUsername(username).orElseThrow();

            Long callId = Long.valueOf(joinData.get("callId").toString());
            
            // Add user to call participants
            callService.addParticipantToCall(callId, user.getId());

            Call call = callService.findById(callId).orElseThrow();

            // Notify other participants that someone joined
            Map<String, Object> notification = new HashMap<>();
            notification.put("type", "USER_JOINED");
            notification.put("callId", callId);
            notification.put("userId", user.getId());
            notification.put("userName", user.getFullName());

            if (call.isGroupCall()) {
                String destination = "/topic/chatroom." + call.getChatRoom().getId() + ".call";
                messagingTemplate.convertAndSend((String)destination, (Object)notification);
            } else {
                // For direct calls, notify the other participant
                Long otherUserId = call.getCaller().getId().equals(user.getId()) 
                    ? call.getConversation().getOtherParticipant(user).getId()
                    : call.getCaller().getId();
                
                messagingTemplate.convertAndSendToUser(
                    otherUserId.toString(),
                    "/queue/call",
                    notification
                );
            }

        } catch (Exception e) {
            e.printStackTrace();
            sendCallError(principal, "Failed to join call: " + e.getMessage());
        }
    }

    /**
     * Leave a call
     * 
     * Payload structure:
     * {
     *   "callId": Long
     * }
     */
    @MessageMapping("/call.leave")
    public void leaveCall(@Payload Map<String, Object> leaveData, Principal principal) {
        try {
            if (principal == null) return;

            String username = principal.getName();
            User user = userService.findByUsername(username).orElseThrow();

            Long callId = Long.valueOf(leaveData.get("callId").toString());
            
            // Remove user from call
            callService.removeParticipantFromCall(callId, user.getId());

            Call call = callService.findById(callId).orElseThrow();

            // Notify other participants that someone left
            Map<String, Object> notification = new HashMap<>();
            notification.put("type", "USER_LEFT");
            notification.put("callId", callId);
            notification.put("userId", user.getId());
            notification.put("userName", user.getFullName());

            if (call.isGroupCall()) {
                String destination = "/topic/chatroom." + call.getChatRoom().getId() + ".call";
                messagingTemplate.convertAndSend((String)destination, (Object)notification);
            } else {
                // For direct calls, notify the other participant and end call
                Long otherUserId = call.getCaller().getId().equals(user.getId()) 
                    ? call.getConversation().getOtherParticipant(user).getId()
                    : call.getCaller().getId();
                
                messagingTemplate.convertAndSendToUser(
                    otherUserId.toString(),
                    "/queue/call",
                    notification
                );

                // End the direct call when one person leaves
                callService.endCall(callId, user.getId());
            }

        } catch (Exception e) {
            e.printStackTrace();
            sendCallError(principal, "Failed to leave call: " + e.getMessage());
        }
    }

    /**
     * Reject incoming call
     * 
     * Payload structure:
     * {
     *   "callId": Long,
     *   "callerId": Long // To notify the caller
     * }
     */
    @MessageMapping("/call.reject")
    public void rejectCall(@Payload Map<String, Object> rejectData, Principal principal) {
        try {
            if (principal == null) return;

            String username = principal.getName();
            User user = userService.findByUsername(username).orElseThrow();

            Long callId = Long.valueOf(rejectData.get("callId").toString());
            Long callerId = Long.valueOf(rejectData.get("callerId").toString());
            
            // Mark call as rejected
            callService.rejectCall(callId, user.getId());

            // Notify caller that call was rejected
            Map<String, Object> notification = new HashMap<>();
            notification.put("type", "CALL_REJECTED");
            notification.put("callId", callId);
            notification.put("userId", user.getId());
            notification.put("userName", user.getFullName());

            messagingTemplate.convertAndSendToUser(
                callerId.toString(),
                "/queue/call",
                notification
            );

        } catch (Exception e) {
            e.printStackTrace();
            sendCallError(principal, "Failed to reject call: " + e.getMessage());
        }
    }

    /**
     * Toggle microphone (mute/unmute)
     * 
     * Payload structure:
     * {
     *   "callId": Long,
     *   "enabled": Boolean
     * }
     */
    @MessageMapping("/call.toggleMicrophone")
    public void toggleMicrophone(@Payload Map<String, Object> micData, Principal principal) {
        try {
            if (principal == null) return;

            String username = principal.getName();
            User user = userService.findByUsername(username).orElseThrow();

            Long callId = Long.valueOf(micData.get("callId").toString());
            
            callService.toggleMicrophone(callId, user.getId());

            Call call = callService.findById(callId).orElseThrow();

            // Notify other participants about mic status change
            Map<String, Object> notification = new HashMap<>();
            notification.put("type", "MIC_TOGGLED");
            notification.put("callId", callId);
            notification.put("userId", user.getId());
            notification.put("enabled", micData.get("enabled"));

            broadcastToCallParticipants(call, notification);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Toggle camera (on/off)
     * 
     * Payload structure:
     * {
     *   "callId": Long,
     *   "enabled": Boolean
     * }
     */
    @MessageMapping("/call.toggleCamera")
    public void toggleCamera(@Payload Map<String, Object> cameraData, Principal principal) {
        try {
            if (principal == null) return;

            String username = principal.getName();
            User user = userService.findByUsername(username).orElseThrow();

            Long callId = Long.valueOf(cameraData.get("callId").toString());
            
            callService.toggleCamera(callId, user.getId());

            Call call = callService.findById(callId).orElseThrow();

            // Notify other participants about camera status change
            Map<String, Object> notification = new HashMap<>();
            notification.put("type", "CAMERA_TOGGLED");
            notification.put("callId", callId);
            notification.put("userId", user.getId());
            notification.put("enabled", cameraData.get("enabled"));

            broadcastToCallParticipants(call, notification);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Toggle screen sharing
     * 
     * Payload structure:
     * {
     *   "callId": Long,
     *   "enabled": Boolean
     * }
     */
    @MessageMapping("/call.toggleScreenShare")
    public void toggleScreenShare(@Payload Map<String, Object> screenData, Principal principal) {
        try {
            if (principal == null) return;

            String username = principal.getName();
            User user = userService.findByUsername(username).orElseThrow();

            Long callId = Long.valueOf(screenData.get("callId").toString());
            
            callService.toggleScreenShare(callId, user.getId());

            Call call = callService.findById(callId).orElseThrow();

            // Notify other participants about screen share status
            Map<String, Object> notification = new HashMap<>();
            notification.put("type", "SCREEN_SHARE_TOGGLED");
            notification.put("callId", callId);
            notification.put("userId", user.getId());
            notification.put("userName", user.getFullName());
            notification.put("enabled", screenData.get("enabled"));

            broadcastToCallParticipants(call, notification);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // ============================================================================
    // HELPER METHODS
    // ============================================================================

    /**
     * Create a standardized call notification object
     */
    private Map<String, Object> createCallNotification(Call call, String notificationType, User caller) {
        Map<String, Object> notification = new HashMap<>();
        notification.put("type", notificationType);
        notification.put("callId", call.getId());
        notification.put("callType", call.getCallType().toString());
        notification.put("callMode", call.getCallMode().toString());
        notification.put("callerId", caller.getId());
        notification.put("callerName", caller.getFullName());
        notification.put("roomId", call.getRoomId());
        
        if (call.isDirectCall()) {
            notification.put("conversationId", call.getConversation().getId());
        } else {
            notification.put("chatRoomId", call.getChatRoom().getId());
            notification.put("chatRoomName", call.getChatRoom().getName());
        }
        
        return notification;
    }

    /**
     * Broadcast message to all participants in a call
     */
    private void broadcastToCallParticipants(Call call, Map<String, Object> message) {
        if (call.isGroupCall()) {
            String destination = "/topic/chatroom." + call.getChatRoom().getId() + ".call";
            messagingTemplate.convertAndSend((String)destination, (Object)message);
        } else {
            // For direct calls, send to both participants
            Long callerId = call.getCaller().getId();
            Long otherUserId = call.getConversation().getOtherParticipant(call.getCaller()).getId();
            
            messagingTemplate.convertAndSendToUser(callerId.toString(), "/queue/call", message);
            messagingTemplate.convertAndSendToUser(otherUserId.toString(), "/queue/call", message);
        }
    }

    /**
     * Send error notification to user
     */
    private void sendCallError(Principal principal, String errorMessage) {
        if (principal == null) return;

        try {
            String username = principal.getName();
            User user = userService.findByUsername(username).orElse(null);
            
            if (user != null) {
                Map<String, Object> error = Map.of(
                    "type", "CALL_ERROR",
                    "message", errorMessage
                );
                
                messagingTemplate.convertAndSendToUser(
                    user.getId().toString(),
                    "/queue/errors",
                    error
                );
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


/**
     * SINGLE unified message handler for both direct and group messages
     * This replaces the old sendDirectMessage and sendGroupMessage methods
     * Handles: /app/chat.sendMessage
     */
    @MessageMapping("/chat.sendMessage")
    public void sendMessage(@Payload Map<String, Object> messageData, Principal principal) {
        try {
            if (principal == null) {
                System.err.println("❌ Principal is null in sendMessage");
                return;
            }

            String username = principal.getName();
            User sender = userService.findByUsername(username)
                    .orElseThrow(() -> new RuntimeException("User not found"));

            String chatType = messageData.get("chatType").toString();
            Long chatId = Long.valueOf(messageData.get("chatId").toString());
            String content = messageData.get("content").toString();

            Message message;
            String destination;
            User recipient = null;

            if ("conversation".equals(chatType) || "direct".equals(chatType)) {
                // Direct message
                message = messageService.sendDirectMessage(sender.getId(), chatId, content);
                destination = "/topic/conversation." + chatId;
                
                // Get recipient for status check
                Conversation conversation = conversationService.findById(chatId);
                recipient = conversation.getOtherParticipant(sender);
                
                // Check if recipient is online and mark as delivered immediately
                if (recipient != null && userService.isUserOnline(recipient.getId())) {
                    message.markAsDelivered();
                    messageService.updateMessage(message);
                }
                
            } else if ("group".equals(chatType) || "chatroom".equals(chatType)) {
                // Group message
                message = messageService.sendGroupMessage(sender.getId(), chatId, content);
                destination = "/topic/chatroom." + chatId;
                
                // Group messages are marked as delivered immediately
                message.markAsDelivered();
                messageService.updateMessage(message);
            } else {
                System.err.println("❌ Invalid chat type: " + chatType);
                return;
            }

            // Create message DTO with delivery status
            MessageDTO messageDTO = MessageDTO.fromEntity(message);
            
            // Broadcast to conversation/chatroom subscribers
            messagingTemplate.convertAndSend(destination, (Object) messageDTO);
            
            // Send delivery acknowledgment back to sender
            Map<String, Object> deliveryAck = new HashMap<>();
            deliveryAck.put("type", "MESSAGE_DELIVERED");
            deliveryAck.put("messageId", message.getId());
            deliveryAck.put("tempId", messageData.get("tempId")); // For client-side tracking
            deliveryAck.put("timestamp", message.getCreatedAt().toString());
            deliveryAck.put("deliveryStatus", message.getDeliveryStatus().name());
            
            messagingTemplate.convertAndSendToUser(
                sender.getId().toString(),
                "/queue/delivery",
                deliveryAck
            );

            // Send notification to recipient if it's a direct message
            if (recipient != null) {
                sendMessageNotification(recipient, sender, message);
            }

            System.out.println("✅ Message sent successfully: " + message.getId() + " Status: " + message.getDeliveryStatus());

        } catch (Exception e) {
            e.printStackTrace();
            System.err.println("❌ Error in sendMessage: " + e.getMessage());
        }
    }


    /**
     * Send notification to recipient about new message
     */
    private void sendMessageNotification(User recipient, User sender, Message message) {
        try {
            Map<String, Object> notification = new HashMap<>();
            notification.put("type", "NEW_MESSAGE");
            notification.put("messageId", message.getId());
            notification.put("senderId", sender.getId());
            notification.put("senderName", sender.getFullName());
            notification.put("content", message.getContent());
            notification.put("timestamp", message.getCreatedAt().toString());
            
            if (message.getConversation() != null) {
                notification.put("conversationId", message.getConversation().getId());
            } else if (message.getChatRoom() != null) {
                notification.put("chatRoomId", message.getChatRoom().getId());
                notification.put("chatRoomName", message.getChatRoom().getName());
            }
            
            messagingTemplate.convertAndSendToUser(
                recipient.getId().toString(),
                "/queue/notifications",
                notification
            );
            
            System.out.println("🔔 Notification sent to user: " + recipient.getId());
            
        } catch (Exception e) {
            System.err.println("❌ Error sending notification: " + e.getMessage());
        }
    }

/**
     * Handle message read receipts
     * When a user reads a message, update status to READ (double tick blue)
     * Handles: /app/chat.messageRead
     */
    @MessageMapping("/chat.messageRead")
    public void handleMessageRead(@Payload Map<String, Object> readData, Principal principal) {
        try {
            if (principal == null) {
                System.err.println("❌ Principal is null in handleMessageRead");
                return;
            }

            String username = principal.getName();
            User reader = userService.findByUsername(username)
                    .orElseThrow(() -> new RuntimeException("User not found"));

            Long messageId = Long.valueOf(readData.get("messageId").toString());
            
            // Mark message as read (sets deliveryStatus to READ)
            messageService.markMessageAsRead(messageId);
            
            // Get the message to find sender
            Message message = messageService.findById(messageId)
                    .orElseThrow(() -> new RuntimeException("Message not found"));
            
            // Send read receipt to message sender (double tick blue)
            Map<String, Object> readReceipt = new HashMap<>();
            readReceipt.put("type", "MESSAGE_READ");
            readReceipt.put("messageId", messageId);
            readReceipt.put("readBy", reader.getId());
            readReceipt.put("readByName", reader.getFullName());
            readReceipt.put("readAt", message.getReadAt().toString());
            readReceipt.put("deliveryStatus", "READ"); // Double tick blue
            
            // Send to original sender
            messagingTemplate.convertAndSendToUser(
                message.getSender().getId().toString(),
                "/queue/read-receipts",
                readReceipt
            );
            
            System.out.println("✅ Read receipt sent for message: " + messageId);

        } catch (Exception e) {
            e.printStackTrace();
            System.err.println("❌ Error in handleMessageRead: " + e.getMessage());
        }
    }

/**
     * Batch mark messages as read (when opening a conversation)
     * Handles: /app/chat.markConversationRead
     */
    @MessageMapping("/chat.markConversationRead")
    public void markConversationRead(@Payload Map<String, Object> readData, Principal principal) {
        try {
            if (principal == null) return;

            String username = principal.getName();
            User reader = userService.findByUsername(username)
                    .orElseThrow(() -> new RuntimeException("User not found"));

            Long conversationId = Long.valueOf(readData.get("conversationId").toString());
            
            // Mark all messages as read
            messageService.markConversationMessagesAsRead(conversationId, reader.getId());
            
            // Get conversation to find other participant
            Conversation conversation = conversationService.findById(conversationId);
            User otherUser = conversation.getOtherParticipant(reader);
            
            // Send batch read receipt to other participant
            Map<String, Object> batchReadReceipt = new HashMap<>();
            batchReadReceipt.put("type", "CONVERSATION_READ");
            batchReadReceipt.put("conversationId", conversationId);
            batchReadReceipt.put("readBy", reader.getId());
            batchReadReceipt.put("readByName", reader.getFullName());
            batchReadReceipt.put("timestamp", java.time.LocalDateTime.now().toString());
            batchReadReceipt.put("deliveryStatus", "READ");
            
            messagingTemplate.convertAndSendToUser(
                otherUser.getId().toString(),
                "/queue/read-receipts",
                batchReadReceipt
            );

            System.out.println("✅ Conversation marked as read: " + conversationId);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}