package com.crm.chat.repository;

import com.crm.chat.entity.ConversationSettings;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for managing conversation settings (pin, mute, hide)
 */
@Repository
public interface ConversationSettingsRepository extends JpaRepository<ConversationSettings, Long> {
    
    /**
     * Find settings for a specific user-to-user conversation
     */
    Optional<ConversationSettings> findByUserIdAndOtherUserId(Long userId, Long otherUserId);
    
    /**
     * Find all pinned conversations for a user
     */
    List<ConversationSettings> findByUserIdAndIsPinnedTrue(Long userId);
    
    /**
     * Find all muted conversations for a user
     */
    List<ConversationSettings> findByUserIdAndIsMutedTrue(Long userId);
    
    /**
     * Find all hidden conversations for a user
     */
    List<ConversationSettings> findByUserIdAndIsHiddenTrue(Long userId);
    
    /**
     * Find all settings for a user (for debugging)
     */
    List<ConversationSettings> findByUserId(Long userId);
    
    /**
     * Delete all settings for a user
     */
    void deleteByUserId(Long userId);
    
    /**
     * Delete settings for a specific conversation
     */
    void deleteByUserIdAndOtherUserId(Long userId, Long otherUserId);
}