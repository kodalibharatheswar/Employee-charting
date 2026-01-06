package com.crm.chat.service;

import com.crm.chat.entity.*;
import com.crm.chat.repository.MessageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional
public class MessageService {

    private final MessageRepository messageRepository;
    private final ConversationService conversationService;
    private final ChatRoomService chatRoomService;
    private final UserService userService;

    // Send message in one-to-one conversation
    public Message sendDirectMessage(Long senderId, Long conversationId, String content) {
        User sender = userService.findById(senderId)
                .orElseThrow(() -> new RuntimeException("Sender not found"));
        Conversation conversation = conversationService.findById(conversationId)
                .orElseThrow(() -> new RuntimeException("Conversation not found"));

        // Verify sender is participant
        if (!conversationService.isParticipant(conversationId, senderId)) {
            throw new RuntimeException("User is not a participant in this conversation");
        }

        Message message = new Message();
        message.setSender(sender);
        message.setConversation(conversation);
        message.setContent(content);
        message.setType(Message.MessageType.TEXT);
        message.setIsRead(false);
        message.setDeleted(false);
        message.setCreatedAt(LocalDateTime.now());

        message = messageRepository.save(message);

        // Update conversation last message time
        conversationService.updateLastMessageTime(conversationId);

        return message;
    }

    // Send message in group chat
    public Message sendGroupMessage(Long senderId, Long chatRoomId, String content) {
        User sender = userService.findById(senderId)
                .orElseThrow(() -> new RuntimeException("Sender not found"));
        ChatRoom chatRoom = chatRoomService.findById(chatRoomId)
                .orElseThrow(() -> new RuntimeException("Chat room not found"));

        // Verify sender is member
        if (!chatRoomService.isMember(chatRoomId, senderId)) {
            throw new RuntimeException("User is not a member of this chat room");
        }

        Message message = new Message();
        message.setSender(sender);
        message.setChatRoom(chatRoom);
        message.setContent(content);
        message.setType(Message.MessageType.TEXT);
        message.setIsRead(false);
        message.setDeleted(false);
        message.setCreatedAt(LocalDateTime.now());

        message = messageRepository.save(message);

        // Update chat room last message time
        chatRoomService.updateLastMessageTime(chatRoomId);

        return message;
    }

    // Get conversation messages
    public List<Message> getConversationMessages(Long conversationId) {
        return messageRepository.findByConversationIdOrderByCreatedAtAsc(conversationId);
    }

    // Get conversation messages with pagination
    public Page<Message> getConversationMessages(Long conversationId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return messageRepository.findByConversationIdOrderByCreatedAtDesc(conversationId, pageable);
    }

    // Get chat room messages
    public List<Message> getChatRoomMessages(Long chatRoomId) {
        return messageRepository.findByChatRoomIdOrderByCreatedAtAsc(chatRoomId);
    }

    // Get chat room messages with pagination
    public Page<Message> getChatRoomMessages(Long chatRoomId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return messageRepository.findByChatRoomIdOrderByCreatedAtDesc(chatRoomId, pageable);
    }

    // Mark message as read
    public void markMessageAsRead(Long messageId) {
        Message message = messageRepository.findById(messageId)
                .orElseThrow(() -> new RuntimeException("Message not found"));
        message.markAsRead();
        messageRepository.save(message);
    }

    // Mark all conversation messages as read
    public void markConversationMessagesAsRead(Long conversationId, Long userId) {
        List<Message> messages = messageRepository.findByConversationIdOrderByCreatedAtAsc(conversationId);
        messages.stream()
                .filter(msg -> !msg.getSender().getId().equals(userId) && !msg.getIsRead())
                .forEach(msg -> {
                    msg.markAsRead();
                    messageRepository.save(msg);
                });
    }

    // Get unread message count for conversation
    public Long getUnreadConversationMessageCount(Long conversationId, Long userId) {
        return messageRepository.countUnreadMessagesInConversation(conversationId, userId);
    }

    // Get unread message count for chat room
    public Long getUnreadChatRoomMessageCount(Long chatRoomId, Long userId) {
        return messageRepository.countUnreadMessagesInChatRoom(chatRoomId, userId);
    }

    // Delete message
    public void deleteMessage(Long messageId) {
        Message message = messageRepository.findById(messageId)
                .orElseThrow(() -> new RuntimeException("Message not found"));
        message.setDeleted(true);
        messageRepository.save(message);
    }

    // Find message by ID
    public Optional<Message> findById(Long messageId) {
        return messageRepository.findById(messageId);
    }
}
