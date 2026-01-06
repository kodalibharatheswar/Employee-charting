package com.crm.chat.service;

import com.crm.chat.entity.ChatRoom;
import com.crm.chat.entity.ChatRoomMember;
import com.crm.chat.entity.User;
import com.crm.chat.repository.ChatRoomMemberRepository;
import com.crm.chat.repository.ChatRoomRepository;
import com.crm.chat.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional
public class ChatRoomService {

    private final ChatRoomRepository chatRoomRepository;
    private final ChatRoomMemberRepository chatRoomMemberRepository;
    private final UserRepository userRepository;

    public ChatRoom createChatRoom(String name, String description, Long createdById, ChatRoom.ChatRoomType type) {
        User creator = userRepository.findById(createdById)
                .orElseThrow(() -> new RuntimeException("User not found"));

        ChatRoom chatRoom = new ChatRoom();
        chatRoom.setName(name);
        chatRoom.setDescription(description);
        chatRoom.setCreatedBy(creator);
        chatRoom.setType(type);
        chatRoom.setActive(true);
        chatRoom.setCreatedAt(LocalDateTime.now());
        chatRoom.setUpdatedAt(LocalDateTime.now());

        chatRoom = chatRoomRepository.save(chatRoom);

        // Add creator as admin
        addMemberToChatRoom(chatRoom.getId(), createdById, ChatRoomMember.MemberRole.ADMIN);

        return chatRoom;
    }

    public void addMemberToChatRoom(Long chatRoomId, Long userId, ChatRoomMember.MemberRole role) {
        ChatRoom chatRoom = chatRoomRepository.findById(chatRoomId)
                .orElseThrow(() -> new RuntimeException("Chat room not found"));
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        // Check if already a member
        if (chatRoomMemberRepository.isMember(chatRoomId, userId)) {
            throw new RuntimeException("User is already a member");
        }

        ChatRoomMember member = new ChatRoomMember();
        member.setChatRoom(chatRoom);
        member.setUser(user);
        member.setRole(role);
        member.setJoinedAt(LocalDateTime.now());
        member.setActive(true);

        chatRoomMemberRepository.save(member);
    }

    public void removeMemberFromChatRoom(Long chatRoomId, Long userId) {
        chatRoomMemberRepository.deleteByChatRoomIdAndUserId(chatRoomId, userId);
    }

    public List<ChatRoom> getUserChatRooms(Long userId) {
        return chatRoomRepository.findByUserId(userId);
    }

    public List<ChatRoom> getAllActiveChatRooms() {
        return chatRoomRepository.findByActiveTrue();
    }

    public Optional<ChatRoom> findById(Long chatRoomId) {
        return chatRoomRepository.findById(chatRoomId);
    }

    public List<ChatRoomMember> getChatRoomMembers(Long chatRoomId) {
        return chatRoomMemberRepository.findByChatRoomId(chatRoomId);
    }

    public boolean isMember(Long chatRoomId, Long userId) {
        return chatRoomMemberRepository.isMember(chatRoomId, userId);
    }

    public void updateLastMessageTime(Long chatRoomId) {
        ChatRoom chatRoom = chatRoomRepository.findById(chatRoomId)
                .orElseThrow(() -> new RuntimeException("Chat room not found"));
        chatRoom.setLastMessageAt(LocalDateTime.now());
        chatRoom.setUpdatedAt(LocalDateTime.now());
        chatRoomRepository.save(chatRoom);
    }

    public void updateMemberLastRead(Long chatRoomId, Long userId) {
        ChatRoomMember member = chatRoomMemberRepository
                .findByChatRoomIdAndUserId(chatRoomId, userId)
                .orElseThrow(() -> new RuntimeException("Member not found"));
        member.setLastReadAt(LocalDateTime.now());
        chatRoomMemberRepository.save(member);
    }

    public Long getUnreadMessageCount(Long chatRoomId, Long userId) {
        return chatRoomRepository.countUnreadMessages(chatRoomId, userId);
    }

    public ChatRoom updateChatRoom(Long chatRoomId, String name, String description) {
        ChatRoom chatRoom = chatRoomRepository.findById(chatRoomId)
                .orElseThrow(() -> new RuntimeException("Chat room not found"));
        chatRoom.setName(name);
        chatRoom.setDescription(description);
        chatRoom.setUpdatedAt(LocalDateTime.now());
        return chatRoomRepository.save(chatRoom);
    }

    public void deleteChatRoom(Long chatRoomId) {
        ChatRoom chatRoom = chatRoomRepository.findById(chatRoomId)
                .orElseThrow(() -> new RuntimeException("Chat room not found"));
        chatRoom.setActive(false);
        chatRoomRepository.save(chatRoom);
    }

    public List<ChatRoom> searchChatRooms(String searchTerm) {
        return chatRoomRepository.searchByName(searchTerm);
    }
}
