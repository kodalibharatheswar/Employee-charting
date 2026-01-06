package com.crm.chat.dto;

import com.crm.chat.entity.ChatRoom;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChatRoomDTO {

    private Long id;
    private String name;
    private String description;
    private Long createdById;
    private String createdByName;
    private Integer memberCount;
    private LocalDateTime lastMessageAt;
    private LocalDateTime createdAt;
    private String type;
    private Long unreadCount;

    // Convert Entity to DTO
    public static ChatRoomDTO fromEntity(ChatRoom chatRoom) {
        ChatRoomDTO dto = new ChatRoomDTO();
        dto.setId(chatRoom.getId());
        dto.setName(chatRoom.getName());
        dto.setDescription(chatRoom.getDescription());
        dto.setCreatedById(chatRoom.getCreatedBy().getId());
        dto.setCreatedByName(chatRoom.getCreatedBy().getFullName());
        dto.setMemberCount(chatRoom.getMembers().size());
        dto.setLastMessageAt(chatRoom.getLastMessageAt());
        dto.setCreatedAt(chatRoom.getCreatedAt());
        dto.setType(chatRoom.getType().name());
        dto.setUnreadCount(0L);
        return dto;
    }
}
