package com.crm.chat.dto;

import com.crm.chat.entity.Message;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MessageDTO {

    private Long id;
    private Long senderId;
    private String senderName;
    private String senderUsername;
    private Long conversationId;
    private Long chatRoomId;
    private String content;
    private MessageType type;
    private Boolean isRead;
    private Boolean isDelivered;
    private LocalDateTime createdAt;
    private LocalDateTime readAt;
    private LocalDateTime deliveredAt;
    private DeliveryStatus deliveryStatus;
    private Long tempId; // For client-side tracking before server response

    public enum MessageType {
        TEXT, IMAGE, FILE, SYSTEM
    }

    /**
     * WhatsApp-style delivery status
     */
    public enum DeliveryStatus {
        SENT,       // Single tick (✓) - Sent to server
        DELIVERED,  // Double tick gray (✓✓) - Delivered to recipient
        READ        // Double tick blue (✓✓) - Read by recipient
    }

    // Update fromEntity method to include status
    public static MessageDTO fromEntity(Message message) {
        MessageDTO dto = new MessageDTO();
        dto.setId(message.getId());
        dto.setSenderId(message.getSender().getId());
        dto.setSenderName(message.getSender().getFullName());
        dto.setSenderUsername(message.getSender().getUsername());

        if (message.getConversation() != null) {
            dto.setConversationId(message.getConversation().getId());
        }

        if (message.getChatRoom() != null) {
            dto.setChatRoomId(message.getChatRoom().getId());
        }

        dto.setContent(message.getContent());
        dto.setType(MessageType.valueOf(message.getType().name()));
        dto.setIsRead(message.getIsRead());
        dto.setIsDelivered(message.getIsDelivered());
        dto.setCreatedAt(message.getCreatedAt());
        dto.setReadAt(message.getReadAt());
        dto.setDeliveredAt(message.getDeliveredAt());

        // Set delivery status
        dto.setDeliveryStatus(DeliveryStatus.valueOf(message.getDeliveryStatus().name()));

        return dto;
    }
}
