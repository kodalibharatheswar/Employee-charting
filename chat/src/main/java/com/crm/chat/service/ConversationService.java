package com.crm.chat.service;

import com.crm.chat.entity.Conversation;
import com.crm.chat.entity.User;
import com.crm.chat.repository.ConversationRepository;
import com.crm.chat.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional
public class ConversationService {

    private final ConversationRepository conversationRepository;
    private final UserRepository userRepository;

    public Conversation createConversation(Long user1Id, Long user2Id) {
        User user1 = userRepository.findById(user1Id)
                .orElseThrow(() -> new RuntimeException("User 1 not found"));
        User user2 = userRepository.findById(user2Id)
                .orElseThrow(() -> new RuntimeException("User 2 not found"));

        Conversation conversation = new Conversation();
        conversation.setCreatedAt(LocalDateTime.now());
        conversation.setUpdatedAt(LocalDateTime.now());

        Set<User> participants = new HashSet<>();
        participants.add(user1);
        participants.add(user2);
        conversation.setParticipants(participants);

        return conversationRepository.save(conversation);
    }

    public Optional<Conversation> findConversationBetweenUsers(Long user1Id, Long user2Id) {
        // Try the optimized query first
        Optional<Conversation> conversation = conversationRepository.findConversationBetweenUsers(user1Id, user2Id);
        
        if (conversation.isPresent()) {
            return conversation;
        }
        
        // Fallback: Get all conversations and return the first one
        List<Conversation> conversations = conversationRepository.findAllConversationsBetweenUsers(user1Id, user2Id);
        
        if (conversations != null && !conversations.isEmpty()) {
            // If multiple conversations exist, return the oldest one
            return Optional.of(conversations.get(0));
        }
        
        return Optional.empty();
    }

    public Conversation getOrCreateConversation(Long user1Id, Long user2Id) {
        // Check if conversation already exists
        Optional<Conversation> existing = findConversationBetweenUsers(user1Id, user2Id);
        
        if (existing.isPresent()) {
            return existing.get();
        }
        
        // Create new conversation if it doesn't exist
        return createConversation(user1Id, user2Id);
    }

    public Conversation findById(Long conversationId) {
        return conversationRepository.findById(conversationId)
                .orElseThrow(() -> new RuntimeException("Conversation not found"));
    }

    public void updateLastMessageTime(Long conversationId) {
        Conversation conversation = findById(conversationId);
        conversation.setLastMessageAt(LocalDateTime.now());
        conversation.setUpdatedAt(LocalDateTime.now());
        conversationRepository.save(conversation);
    }
    
    /**
     * Check if a user is a participant in a conversation
     */
    public boolean isParticipant(Long conversationId, Long userId) {
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new RuntimeException("Conversation not found"));
        
        return conversation.getParticipants().stream()
                .anyMatch(user -> user.getId().equals(userId));
    }
    
    /**
     * Clean up duplicate conversations for a specific user pair
     * Keeps the oldest conversation and removes duplicates
     */
    @Transactional
    public void cleanupDuplicateConversations(Long user1Id, Long user2Id) {
        List<Conversation> conversations = conversationRepository.findAllConversationsBetweenUsers(user1Id, user2Id);
        
        if (conversations.size() <= 1) {
            // No duplicates, nothing to clean
            return;
        }
        
        // Keep the oldest conversation (first in list if ordered by created_at)
        Conversation toKeep = conversations.get(0);
        
        // Delete the rest
        for (int i = 1; i < conversations.size(); i++) {
            conversationRepository.delete(conversations.get(i));
        }
    }
}