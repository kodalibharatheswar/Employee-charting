package com.crm.chat.controller;

import com.crm.chat.dto.ChatRoomDTO;
import com.crm.chat.dto.MessageDTO;
import com.crm.chat.dto.UserDTO;
import com.crm.chat.entity.*;
import com.crm.chat.service.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Controller
@RequiredArgsConstructor
public class ChatController {

    private final UserService userService;
    private final ConversationService conversationService;
    private final ChatRoomService chatRoomService;
    private final MessageService messageService;

    private User getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String username = auth.getName();
        return userService.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));
    }

    @GetMapping("/chat")
    public String chatPage(Model model) {
        User currentUser = getCurrentUser();
        userService.setUserOnline(currentUser.getId());

        model.addAttribute("currentUser", currentUser);
        model.addAttribute("users", userService.getAllActiveUsersExcept(currentUser.getId()));
        model.addAttribute("conversations", conversationService.getUserConversations(currentUser.getId()));
        model.addAttribute("chatRooms", chatRoomService.getUserChatRooms(currentUser.getId()));

        return "chat";
    }

    // REST API Endpoints

    @GetMapping("/api/users")
    @ResponseBody
    public List<UserDTO> getAllUsers() {
        User currentUser = getCurrentUser();
        return userService.getAllActiveUsersExcept(currentUser.getId())
                .stream()
                .map(UserDTO::fromEntity)
                .collect(Collectors.toList());
    }

    @GetMapping("/api/users/search")
    @ResponseBody
    public List<UserDTO> searchUsers(@RequestParam String query) {
        User currentUser = getCurrentUser();
        return userService.searchUsers(query, currentUser.getId())
                .stream()
                .map(UserDTO::fromEntity)
                .collect(Collectors.toList());
    }

    @PostMapping("/api/conversations")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> createConversation(@RequestParam Long recipientId) {
        User currentUser = getCurrentUser();
        Conversation conversation = conversationService.getOrCreateConversation(
                currentUser.getId(), recipientId);

        Map<String, Object> response = new HashMap<>();
        response.put("conversationId", conversation.getId());
        response.put("recipient", UserDTO.fromEntity(
                conversation.getOtherParticipant(currentUser)));

        return ResponseEntity.ok(response);
    }

    @GetMapping("/api/conversations/{conversationId}/messages")
    @ResponseBody
    public List<MessageDTO> getConversationMessages(@PathVariable Long conversationId) {
        User currentUser = getCurrentUser();

        // Verify user is participant
        if (!conversationService.isParticipant(conversationId, currentUser.getId())) {
            throw new RuntimeException("Unauthorized");
        }

        // Mark messages as read
        messageService.markConversationMessagesAsRead(conversationId, currentUser.getId());

        return messageService.getConversationMessages(conversationId)
                .stream()
                .map(MessageDTO::fromEntity)
                .collect(Collectors.toList());
    }

    @PostMapping("/api/conversations/{conversationId}/messages")
    @ResponseBody
    public MessageDTO sendDirectMessage(@PathVariable Long conversationId,
                                        @RequestBody Map<String, String> payload) {
        User currentUser = getCurrentUser();
        String content = payload.get("content");

        Message message = messageService.sendDirectMessage(
                currentUser.getId(), conversationId, content);

        return MessageDTO.fromEntity(message);
    }

    @PostMapping("/api/chatrooms")
    @ResponseBody
    public ChatRoomDTO createChatRoom(@RequestBody Map<String, String> payload) {
        User currentUser = getCurrentUser();
        String name = payload.get("name");
        String description = payload.get("description");
        String typeStr = payload.getOrDefault("type", "GROUP");

        ChatRoom.ChatRoomType type = ChatRoom.ChatRoomType.valueOf(typeStr);
        ChatRoom chatRoom = chatRoomService.createChatRoom(
                name, description, currentUser.getId(), type);

        return ChatRoomDTO.fromEntity(chatRoom);
    }

    @GetMapping("/api/chatrooms")
    @ResponseBody
    public List<ChatRoomDTO> getUserChatRooms() {
        User currentUser = getCurrentUser();
        return chatRoomService.getUserChatRooms(currentUser.getId())
                .stream()
                .map(ChatRoomDTO::fromEntity)
                .collect(Collectors.toList());
    }

    @GetMapping("/api/chatrooms/{chatRoomId}/messages")
    @ResponseBody
    public List<MessageDTO> getChatRoomMessages(@PathVariable Long chatRoomId) {
        User currentUser = getCurrentUser();

        // Verify user is member
        if (!chatRoomService.isMember(chatRoomId, currentUser.getId())) {
            throw new RuntimeException("Unauthorized");
        }

        // Update last read time
        chatRoomService.updateMemberLastRead(chatRoomId, currentUser.getId());

        return messageService.getChatRoomMessages(chatRoomId)
                .stream()
                .map(MessageDTO::fromEntity)
                .collect(Collectors.toList());
    }

    @PostMapping("/api/chatrooms/{chatRoomId}/messages")
    @ResponseBody
    public MessageDTO sendGroupMessage(@PathVariable Long chatRoomId,
                                       @RequestBody Map<String, String> payload) {
        User currentUser = getCurrentUser();
        String content = payload.get("content");

        Message message = messageService.sendGroupMessage(
                currentUser.getId(), chatRoomId, content);

        return MessageDTO.fromEntity(message);
    }

    @PostMapping("/api/chatrooms/{chatRoomId}/members")
    @ResponseBody
    public ResponseEntity<String> addMemberToChatRoom(@PathVariable Long chatRoomId,
                                                      @RequestBody Map<String, Object> payload) {
        Long userId = Long.valueOf(payload.get("userId").toString());
        String roleStr = payload.getOrDefault("role", "MEMBER").toString();
        ChatRoomMember.MemberRole role = ChatRoomMember.MemberRole.valueOf(roleStr);

        chatRoomService.addMemberToChatRoom(chatRoomId, userId, role);
        return ResponseEntity.ok("Member added successfully");
    }

    @GetMapping("/api/chatrooms/{chatRoomId}/members")
    @ResponseBody
    public List<UserDTO> getChatRoomMembers(@PathVariable Long chatRoomId) {
        return chatRoomService.getChatRoomMembers(chatRoomId)
                .stream()
                .map(member -> UserDTO.fromEntity(member.getUser()))
                .collect(Collectors.toList());
    }

    @DeleteMapping("/api/chatrooms/{chatRoomId}/members/{userId}")
    @ResponseBody
    public ResponseEntity<String> removeMemberFromChatRoom(@PathVariable Long chatRoomId,
                                                           @PathVariable Long userId) {
        chatRoomService.removeMemberFromChatRoom(chatRoomId, userId);
        return ResponseEntity.ok("Member removed successfully");
    }
}
