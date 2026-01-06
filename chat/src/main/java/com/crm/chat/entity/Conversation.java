package com.crm.chat.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
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
@Table(name = "conversations", indexes = {
        @Index(name = "idx_conversation_updated", columnList = "updated_at")
})
// @Data
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Conversation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToMany
    @JoinTable(
            name = "conversation_participants",
            joinColumns = @JoinColumn(name = "conversation_id"),
            inverseJoinColumns = @JoinColumn(name = "user_id")
    )
    private Set<User> participants = new HashSet<>();

    @OneToMany(mappedBy = "conversation", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<Message> messages = new HashSet<>();

    @Column
    private LocalDateTime lastMessageAt;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    // Helper methods
    public void addParticipant(User user) {
        this.participants.add(user);
        user.getConversations().add(this);
    }

    public void removeParticipant(User user) {
        this.participants.remove(user);
        user.getConversations().remove(this);
    }

    public User getOtherParticipant(User currentUser) {
        return participants.stream()
                .filter(user -> !user.getId().equals(currentUser.getId()))
                .findFirst()
                .orElse(null);
    }

    public boolean hasParticipant(User user) {
        return participants.stream()
                .anyMatch(p -> p.getId().equals(user.getId()));
    }

    public void updateLastMessageTime() {
        this.lastMessageAt = LocalDateTime.now();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Conversation that)) return false;
        return id != null && id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
