package com.crm.chat.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

/**
 * Entity for storing user-specific conversation settings
 * Includes pin, mute, and hide functionality
 */
@Entity
@Table(name = "conversation_settings")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ConversationSettings {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    /**
     * The user who owns these settings
     */
    @Column(name = "user_id", nullable = false)
    private Long userId;
    
    /**
     * The other user in the conversation
     */
    @Column(name = "other_user_id", nullable = false)
    private Long otherUserId;
    
    /**
     * Whether this conversation is pinned
     */
    @Column(name = "is_pinned")
    private Boolean isPinned = false;
    
    /**
     * Whether notifications are muted
     */
    @Column(name = "is_muted")
    private Boolean isMuted = false;
    
    /**
     * Whether conversation is hidden from list
     */
    @Column(name = "is_hidden")
    private Boolean isHidden = false;
    
    /**
     * When the conversation was pinned
     */
    @Column(name = "pinned_at")
    private LocalDateTime pinnedAt;
    
    /**
     * When the conversation was muted
     */
    @Column(name = "muted_at")
    private LocalDateTime mutedAt;
    
    /**
     * When the conversation was hidden
     */
    @Column(name = "hidden_at")
    private LocalDateTime hiddenAt;
    
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
    
    @Column(name = "updated_at")
    private LocalDateTime updatedAt = LocalDateTime.now();
    
    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}