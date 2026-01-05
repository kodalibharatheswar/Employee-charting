package com.crm.chat.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "chat_room_members", indexes = {
        @Index(name = "idx_chatroom_user", columnList = "chat_room_id,user_id")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChatRoomMember {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "chat_room_id", nullable = false)
    private ChatRoom chatRoom;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MemberRole role = MemberRole.MEMBER;

    @Column(nullable = false)
    private LocalDateTime joinedAt;

    @Column
    private LocalDateTime lastReadAt;

    @Column(nullable = false)
    private Boolean active = true;

    public enum MemberRole {
        ADMIN, MODERATOR, MEMBER
    }

    // Helper methods
    public boolean isAdmin() {
        return this.role == MemberRole.ADMIN;
    }

    public boolean isModerator() {
        return this.role == MemberRole.MODERATOR || this.role == MemberRole.ADMIN;
    }

    public void updateLastRead() {
        this.lastReadAt = LocalDateTime.now();
    }
}
