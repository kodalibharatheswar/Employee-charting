package com.crm.chat.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.Objects;

@Entity
@Table(name = "chat_room_members", indexes = {
        @Index(name = "idx_chatroom_user", columnList = "chat_room_id,user_id")
})
// @Data
@Getter
@Setter
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

     @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ChatRoomMember that)) return false;
        return id != null && id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
