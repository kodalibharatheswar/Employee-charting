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
import org.springframework.web.multipart.MultipartFile;


import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

@Controller
@RequiredArgsConstructor
public class ChatController {

    private final UserService userService;
    private final ConversationService conversationService;
    private final ChatRoomService chatRoomService;
    private final MessageService messageService;
    private final org.springframework.messaging.simp.SimpMessagingTemplate messagingTemplate;

    // Define a storage location
    private final String UPLOAD_DIR = "src/main/resources/static/uploads/";


    /**
     * Helper to check if the current user has ADMIN privileges in a specific chat room.
     */
    private boolean isCurrentUserAdmin(Long chatRoomId) {
        User currentUser = getCurrentUser();
        return chatRoomService.getChatRoomMembers(chatRoomId).stream()
                .anyMatch(m -> m.getUser().getId().equals(currentUser.getId()) && 
                          m.getRole() == ChatRoomMember.MemberRole.ADMIN);
    }


    @PostMapping("/api/chat/upload")
    public ResponseEntity<MessageDTO> uploadFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam("chatId") Long chatId,
            @RequestParam("chatType") String chatType) throws IOException {

        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        User currentUser = userService.findByUsername(username).orElseThrow();

        Path uploadPath = Paths.get(UPLOAD_DIR);
        if (!Files.exists(uploadPath)) {
            Files.createDirectories(uploadPath);
        }

        String fileName = UUID.randomUUID().toString() + "_" + file.getOriginalFilename();
        Path filePath = uploadPath.resolve(fileName);
        Files.copy(file.getInputStream(), filePath);

        String fileUrl = "/uploads/" + fileName;

        Message message;
        if ("direct".equals(chatType)) {
            message = messageService.sendDirectMessage(currentUser.getId(), chatId, fileUrl);
        } else {
            message = messageService.sendGroupMessage(currentUser.getId(), chatId, fileUrl);
        }
        message.setType(Message.MessageType.FILE);
        
        return ResponseEntity.ok(MessageDTO.fromEntity(message));
    }

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


    @PostMapping("/api/chatrooms/{chatRoomId}/members/batch")
@ResponseBody
public ResponseEntity<String> addMembersBatch(@PathVariable Long chatRoomId, 
                                             @RequestBody List<Long> userIds) {
    for (Long userId : userIds) {
        try {
            chatRoomService.addMemberToChatRoom(chatRoomId, userId, ChatRoomMember.MemberRole.MEMBER);
        } catch (Exception e) {
            // Ignore if already a member or log error
        }
    }
    return ResponseEntity.ok("Members added");
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
        ChatRoom chatRoom = chatRoomService.createChatRoom(
                payload.get("name"), payload.get("description"), 
                currentUser.getId(), ChatRoom.ChatRoomType.valueOf(payload.getOrDefault("type", "GROUP")));
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


    @PutMapping("/api/chatrooms/{chatRoomId}")
    @ResponseBody
    public ChatRoomDTO updateChatRoom(@PathVariable Long chatRoomId, @RequestBody Map<String, String> payload) {
        // Fix: Check for ADMIN role instead of creator ID
        if (!isCurrentUserAdmin(chatRoomId)) {
            throw new RuntimeException("Unauthorized: Only group admins can update group details.");
        }
        ChatRoom updated = chatRoomService.updateChatRoom(chatRoomId, payload.get("name"), payload.get("description"));
        return ChatRoomDTO.fromEntity(updated);
    }


    @PutMapping("/api/chatrooms/{chatRoomId}/members/{userId}/role")
    @ResponseBody
    public ResponseEntity<String> updateMemberRole(@PathVariable Long chatRoomId, @PathVariable Long userId, @RequestBody Map<String, String> payload) {
        // Fix: Check for ADMIN role instead of creator ID
        if (!isCurrentUserAdmin(chatRoomId)) {
            throw new RuntimeException("Unauthorized: Only group admins can change roles.");
        }
        
        // Get the user whose role is being updated
        User updatedUser = userService.findById(userId)
        .orElseThrow(() -> new RuntimeException("User not found"));
        String newRole = payload.get("role");
        
        chatRoomService.updateMemberRole(chatRoomId, userId, ChatRoomMember.MemberRole.valueOf(newRole));
        
        // Send WebSocket notification to all members of the chat room
        Map<String, Object> notification = new HashMap<>();
        notification.put("type", "ROLE_UPDATE");
        notification.put("userId", userId);
        notification.put("userName", updatedUser.getFullName());
        notification.put("newRole", newRole);
        notification.put("message", updatedUser.getFullName() + " is now " + (newRole.equals("ADMIN") ? "an admin" : "a member"));
        
        messagingTemplate.convertAndSend("/topic/chatroom." + chatRoomId, (Object) notification);
        
        return ResponseEntity.ok("Updated");
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
    public ResponseEntity<String> addMember(@PathVariable Long chatRoomId, @RequestBody Map<String, Object> payload) {
        // Permission check: Only admins can add new members
        if (!isCurrentUserAdmin(chatRoomId)) {
            throw new RuntimeException("Unauthorized: Only group admins can add new members.");
        }
        Long userId = Long.valueOf(payload.get("userId").toString());
        chatRoomService.addMemberToChatRoom(chatRoomId, userId, ChatRoomMember.MemberRole.MEMBER);
        return ResponseEntity.ok("Added");
    }

    @GetMapping("/api/chatrooms/{chatRoomId}/members")
    @ResponseBody
    public List<Map<String, Object>> getChatRoomMembers(@PathVariable Long chatRoomId) {
        return chatRoomService.getChatRoomMembers(chatRoomId)
                .stream()
                .map(member -> {
                    Map<String, Object> map = new HashMap<>();
                    map.put("id", member.getUser().getId());
                    map.put("fullName", member.getUser().getFullName());
                    map.put("department", member.getUser().getDepartment());
                    map.put("role", member.getRole().name()); // Crucial: Fixes the 'undefined' role bug
                    return map;
                })
                .collect(Collectors.toList());
    }

    @DeleteMapping("/api/chatrooms/{chatRoomId}/members/{userId}")
    @ResponseBody
    public ResponseEntity<String> removeMember(@PathVariable Long chatRoomId, @PathVariable Long userId) {
        User currentUser = getCurrentUser();
        // Permission check: Admin can remove anyone; non-admins can only remove themselves (leave)
        if (!isCurrentUserAdmin(chatRoomId) && !userId.equals(currentUser.getId())) {
            throw new RuntimeException("Unauthorized: Only admins can remove other members.");
        }
        chatRoomService.removeMemberFromChatRoom(chatRoomId, userId);
        return ResponseEntity.ok("Removed");
    }
}