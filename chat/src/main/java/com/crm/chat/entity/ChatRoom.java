package com.crm.chat.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
// import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.Objects;

@Entity
@Table(name = "chat_rooms", indexes = {
        @Index(name = "idx_chatroom_updated", columnList = "updated_at")
})
// @Data
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ChatRoom {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(length = 500)
    private String description;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by", nullable = false)
    private User createdBy;

    @OneToMany(mappedBy = "chatRoom", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<ChatRoomMember> members = new HashSet<>();

    @OneToMany(mappedBy = "chatRoom", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<Message> messages = new HashSet<>();

    @Column
    private LocalDateTime lastMessageAt;

    @Column(nullable = false)
    private Boolean active = true;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ChatRoomType type = ChatRoomType.GROUP;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    public enum ChatRoomType {
        GROUP, CHANNEL, PROJECT, DEPARTMENT
    }

    // Helper methods
    public void addMember(User user, ChatRoomMember.MemberRole role) {
        ChatRoomMember member = new ChatRoomMember();
        member.setChatRoom(this);
        member.setUser(user);
        member.setRole(role);
        member.setJoinedAt(LocalDateTime.now());
        this.members.add(member);
    }

    public void removeMember(User user) {
        members.removeIf(member -> member.getUser().getId().equals(user.getId()));
    }

    public boolean hasMember(User user) {
        return members.stream()
                .anyMatch(member -> member.getUser().getId().equals(user.getId()));
    }

    public int getMemberCount() {
        return members.size();
    }

    public void updateLastMessageTime() {
        this.lastMessageAt = LocalDateTime.now();
    }


   @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ChatRoom chatRoom)) return false;
        return id != null && id.equals(chatRoom.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    } 
}
