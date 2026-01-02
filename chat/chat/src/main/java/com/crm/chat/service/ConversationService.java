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
        // Check if conversation already exists
        Optional<Conversation> existing = conversationRepository
                .findConversationBetweenUsers(user1Id, user2Id);

        if (existing.isPresent()) {
            return existing.get();
        }

        User user1 = userRepository.findById(user1Id)
                .orElseThrow(() -> new RuntimeException("User 1 not found"));
        User user2 = userRepository.findById(user2Id)
                .orElseThrow(() -> new RuntimeException("User 2 not found"));

        Conversation conversation = new Conversation();
        Set<User> participants = new HashSet<>();
        participants.add(user1);
        participants.add(user2);
        conversation.setParticipants(participants);
        conversation.setCreatedAt(LocalDateTime.now());
        conversation.setUpdatedAt(LocalDateTime.now());

        return conversationRepository.save(conversation);
    }

    public Optional<Conversation> findConversationBetweenUsers(Long user1Id, Long user2Id) {
        return conversationRepository.findConversationBetweenUsers(user1Id, user2Id);
    }

    public Conversation getOrCreateConversation(Long user1Id, Long user2Id) {
        return findConversationBetweenUsers(user1Id, user2Id)
                .orElseGet(() -> createConversation(user1Id, user2Id));
    }

    public List<Conversation> getUserConversations(Long userId) {
        return conversationRepository.findByParticipantId(userId);
    }

    public Optional<Conversation> findById(Long conversationId) {
        return conversationRepository.findById(conversationId);
    }

    public void updateLastMessageTime(Long conversationId) {
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new RuntimeException("Conversation not found"));
        conversation.setLastMessageAt(LocalDateTime.now());
        conversation.setUpdatedAt(LocalDateTime.now());
        conversationRepository.save(conversation);
    }

    public Long getUnreadMessageCount(Long conversationId, Long userId) {
        return conversationRepository.countUnreadMessages(conversationId, userId);
    }

    public void deleteConversation(Long conversationId) {
        conversationRepository.deleteById(conversationId);
    }

    public boolean isParticipant(Long conversationId, Long userId) {
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElse(null);
        if (conversation == null) {
            return false;
        }
        return conversation.getParticipants().stream()
                .anyMatch(user -> user.getId().equals(userId));
    }
}
