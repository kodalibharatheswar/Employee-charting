package com.crm.chat.service;

import com.crm.chat.entity.ConversationSettings;
import com.crm.chat.repository.ConversationSettingsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Service for managing conversation settings (pin, mute, hide)
 */
@Service
@RequiredArgsConstructor
@Transactional
public class ConversationSettingsService {
    
    private final ConversationSettingsRepository repository;
    
    /**
     * Pin a conversation for a user
     */
    public void pinConversation(Long userId, Long otherUserId) {
        ConversationSettings settings = getOrCreateSettings(userId, otherUserId);
        settings.setIsPinned(true);
        settings.setPinnedAt(LocalDateTime.now());
        repository.save(settings);
    }
    
    /**
     * Unpin a conversation for a user
     */
    public void unpinConversation(Long userId, Long otherUserId) {
        ConversationSettings settings = getOrCreateSettings(userId, otherUserId);
        settings.setIsPinned(false);
        settings.setPinnedAt(null);
        repository.save(settings);
    }
    
    /**
     * Mute a conversation for a user
     */
    public void muteConversation(Long userId, Long otherUserId) {
        ConversationSettings settings = getOrCreateSettings(userId, otherUserId);
        settings.setIsMuted(true);
        settings.setMutedAt(LocalDateTime.now());
        repository.save(settings);
    }
    
    /**
     * Unmute a conversation for a user
     */
    public void unmuteConversation(Long userId, Long otherUserId) {
        ConversationSettings settings = getOrCreateSettings(userId, otherUserId);
        settings.setIsMuted(false);
        settings.setMutedAt(null);
        repository.save(settings);
    }
    
    /**
     * Hide a conversation for a user
     */
    public void hideConversation(Long userId, Long otherUserId) {
        ConversationSettings settings = getOrCreateSettings(userId, otherUserId);
        settings.setIsHidden(true);
        settings.setHiddenAt(LocalDateTime.now());
        repository.save(settings);
    }
    
    /**
     * Unhide a conversation for a user
     */
    public void unhideConversation(Long userId, Long otherUserId) {
        ConversationSettings settings = getOrCreateSettings(userId, otherUserId);
        settings.setIsHidden(false);
        settings.setHiddenAt(null);
        repository.save(settings);
    }
    
    /**
     * Get all pinned conversations for a user
     */
    @Transactional(readOnly = true)
    public List<ConversationSettings> getPinnedConversations(Long userId) {
        return repository.findByUserIdAndIsPinnedTrue(userId);
    }
    
    /**
     * Get all muted conversations for a user
     */
    @Transactional(readOnly = true)
    public List<ConversationSettings> getMutedConversations(Long userId) {
        return repository.findByUserIdAndIsMutedTrue(userId);
    }
    
    /**
     * Get all hidden conversations for a user
     */
    @Transactional(readOnly = true)
    public List<ConversationSettings> getHiddenConversations(Long userId) {
        return repository.findByUserIdAndIsHiddenTrue(userId);
    }
    
    /**
     * Get settings for a specific conversation
     */
    @Transactional(readOnly = true)
    public ConversationSettings getSettings(Long userId, Long otherUserId) {
        return repository.findByUserIdAndOtherUserId(userId, otherUserId)
            .orElse(null);
    }
    
    /**
     * Check if conversation is pinned
     */
    @Transactional(readOnly = true)
    public boolean isPinned(Long userId, Long otherUserId) {
        return repository.findByUserIdAndOtherUserId(userId, otherUserId)
            .map(ConversationSettings::getIsPinned)
            .orElse(false);
    }
    
    /**
     * Check if conversation is muted
     */
    @Transactional(readOnly = true)
    public boolean isMuted(Long userId, Long otherUserId) {
        return repository.findByUserIdAndOtherUserId(userId, otherUserId)
            .map(ConversationSettings::getIsMuted)
            .orElse(false);
    }
    
    /**
     * Check if conversation is hidden
     */
    @Transactional(readOnly = true)
    public boolean isHidden(Long userId, Long otherUserId) {
        return repository.findByUserIdAndOtherUserId(userId, otherUserId)
            .map(ConversationSettings::getIsHidden)
            .orElse(false);
    }
    
    /**
     * Get or create settings for a conversation
     */
    private ConversationSettings getOrCreateSettings(Long userId, Long otherUserId) {
        return repository.findByUserIdAndOtherUserId(userId, otherUserId)
            .orElseGet(() -> {
                ConversationSettings newSettings = new ConversationSettings();
                newSettings.setUserId(userId);
                newSettings.setOtherUserId(otherUserId);
                newSettings.setIsPinned(false);
                newSettings.setIsMuted(false);
                newSettings.setIsHidden(false);
                newSettings.setCreatedAt(LocalDateTime.now());
                newSettings.setUpdatedAt(LocalDateTime.now());
                return newSettings;
            });
    }
    
    /**
     * Delete all settings for a user
     */
    public void deleteUserSettings(Long userId) {
        repository.deleteByUserId(userId);
    }
    
    /**
     * Delete settings for a specific conversation
     */
    public void deleteConversationSettings(Long userId, Long otherUserId) {
        repository.deleteByUserIdAndOtherUserId(userId, otherUserId);
    }
}