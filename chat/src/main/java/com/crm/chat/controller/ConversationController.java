package com.crm.chat.controller;

import com.crm.chat.entity.ConversationSettings;
import com.crm.chat.entity.User;
import com.crm.chat.service.ConversationSettingsService;
import com.crm.chat.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST API Controller for Conversation Settings Management
 * Add these methods to your existing ChatController or create a separate controller
 */
@RestController
@RequestMapping("/api/conversations")
@RequiredArgsConstructor
public class ConversationController {

    private final ConversationSettingsService settingsService;
    private final UserService userService;

    private User getCurrentUser() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        return userService.findByUsername(username)
            .orElseThrow(() -> new RuntimeException("User not found"));
    }

    /**
     * Pin a conversation
     * POST /api/conversations/pin/{otherUserId}
     */
    @PostMapping("/pin/{otherUserId}")
    public ResponseEntity<Map<String, Object>> pinConversation(@PathVariable Long otherUserId) {
        try {
            User currentUser = getCurrentUser();
            settingsService.pinConversation(currentUser.getId(), otherUserId);
            return ResponseEntity.ok(Map.of("success", true, "message", "Pinned"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    /**
     * Unpin a conversation
     * POST /api/conversations/unpin/{otherUserId}
     */
  @PostMapping("/unpin/{otherUserId}")
    public ResponseEntity<Map<String, Object>> unpinConversation(@PathVariable Long otherUserId) {
        try {
            User currentUser = getCurrentUser();
            settingsService.unpinConversation(currentUser.getId(), otherUserId);
            return ResponseEntity.ok(Map.of("success", true, "message", "Unpinned"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    /**
     * Mute a conversation
     * POST /api/conversations/mute/{otherUserId}
     */
    @PostMapping("/mute/{otherUserId}")
    public ResponseEntity<Map<String, Object>> muteConversation(@PathVariable Long otherUserId) {
        try {
            User currentUser = getCurrentUser();
            settingsService.muteConversation(currentUser.getId(), otherUserId);
            return ResponseEntity.ok(Map.of("success", true, "message", "Conversation muted successfully"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    /**
     * Unmute a conversation
     * POST /api/conversations/unmute/{otherUserId}
     */
     @PostMapping("/unmute/{otherUserId}")
    public ResponseEntity<Map<String, Object>> unmuteConversation(@PathVariable Long otherUserId) {
        try {
            User currentUser = getCurrentUser();
            settingsService.unmuteConversation(currentUser.getId(), otherUserId);
            return ResponseEntity.ok(Map.of("success", true, "message", "Conversation unmuted successfully"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    @PostMapping("/unread/{otherUserId}")
    public ResponseEntity<Map<String, Object>> markAsUnread(@PathVariable Long otherUserId) {
        try {
            // Logic for unread is handled visually on frontend for immediate feedback
            // and can be persisted here if you add a 'markedUnread' flag to ConversationSettings
            return ResponseEntity.ok(Map.of("success", true, "message", "Marked as unread"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    /**
     * Hide a conversation
     * POST /api/conversations/hide/{otherUserId}
     */
    @PostMapping("/hide/{otherUserId}")
    public ResponseEntity<Map<String, Object>> hideConversation(@PathVariable Long otherUserId) {
        try {
            User currentUser = getCurrentUser();
            settingsService.hideConversation(currentUser.getId(), otherUserId);
            
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Conversation hidden successfully"
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "message", "Failed to hide conversation: " + e.getMessage()
            ));
        }
    }

    /**
     * Get pinned conversations
     * GET /api/conversations/pinned
     */
    @GetMapping("/pinned")
    public ResponseEntity<?> getPinnedConversations() {
        try {
            User currentUser = getCurrentUser();
            List<ConversationSettings> pinnedList = 
                settingsService.getPinnedConversations(currentUser.getId());
            
            return ResponseEntity.ok(Map.of(
                "success", true,
                "pinned", pinnedList
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "message", "Failed to get pinned conversations: " + e.getMessage()
            ));
        }
    }

    /**
     * Get muted conversations
     * GET /api/conversations/muted
     */
    @GetMapping("/muted")
    public ResponseEntity<?> getMutedConversations() {
        try {
            User currentUser = getCurrentUser();
            List<ConversationSettings> mutedList = 
                settingsService.getMutedConversations(currentUser.getId());
            
            return ResponseEntity.ok(Map.of(
                "success", true,
                "muted", mutedList
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "message", "Failed to get muted conversations: " + e.getMessage()
            ));
        }
    }

    /**
     * Get settings for a specific conversation
     * GET /api/conversations/settings/{otherUserId}
     */
    @GetMapping("/settings/{otherUserId}")
    public ResponseEntity<?> getConversationSettings(@PathVariable Long otherUserId) {
        try {
            User currentUser = getCurrentUser();
            ConversationSettings settings = settingsService.getSettings(currentUser.getId(), otherUserId);
            if (settings == null) {
                return ResponseEntity.ok(Map.of("isPinned", false, "isMuted", false, "isHidden", false));
            }
            return ResponseEntity.ok(settings);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        }
    }
    
}