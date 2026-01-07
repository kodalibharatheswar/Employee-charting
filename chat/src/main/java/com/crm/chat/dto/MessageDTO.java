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
    private LocalDateTime createdAt;
    private LocalDateTime readAt;

    public enum MessageType {
        TEXT, IMAGE, FILE, SYSTEM
    }

    // Convert Entity to DTO
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
        dto.setCreatedAt(message.getCreatedAt());
        dto.setReadAt(message.getReadAt());

        return dto;
    }
}
