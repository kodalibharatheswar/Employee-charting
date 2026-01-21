package com.crm.chat.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.Objects;

@Entity
@Table(name = "messages", indexes = {
        @Index(name = "idx_conversation_created", columnList = "conversation_id,created_at"),
        @Index(name = "idx_chatroom_created", columnList = "chat_room_id,created_at")
})
// @Data
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Message {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sender_id", nullable = false)
    private User sender;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "conversation_id")
    private Conversation conversation;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "chat_room_id")
    private ChatRoom chatRoom;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MessageType type = MessageType.TEXT;

    @Column(nullable = false)
    private Boolean isRead = false;

    @Column
    private LocalDateTime readAt;

    @Column(nullable = false)
    private Boolean isDelivered = false;

    @Column
    private LocalDateTime deliveredAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DeliveryStatus deliveryStatus = DeliveryStatus.SENT;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private Boolean deleted = false;

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

    // Helper methods
    public boolean isDirectMessage() {
        return conversation != null;
    }

    public boolean isGroupMessage() {
        return chatRoom != null;
    }

    public void markAsRead() {
        this.isRead = true;
        this.readAt = LocalDateTime.now();
        this.deliveryStatus = DeliveryStatus.READ;  // ← ADD THIS LINE
    }

    public void markAsDelivered() {
        this.isDelivered = true;
        this.deliveredAt = LocalDateTime.now();
        if (this.deliveryStatus == DeliveryStatus.SENT) {
            this.deliveryStatus = DeliveryStatus.DELIVERED;
        }
    }


    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Message message)) return false;
        return id != null && id.equals(message.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
