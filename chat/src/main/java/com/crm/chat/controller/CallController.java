package com.crm.chat.controller;

import com.crm.chat.dto.CallDTO;
import com.crm.chat.entity.Call;
import com.crm.chat.entity.User;
import com.crm.chat.service.CallService;
import com.crm.chat.service.UserService;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * REST API Controller for Call Management
 * Handles HTTP requests for call-related operations
 */
@RestController
@RequestMapping("/api/calls")
@RequiredArgsConstructor
public class CallController {

    private final CallService callService;
    private final UserService userService;

    /**
     * Get current user ID from authentication
     */
    private Long getCurrentUserId(Authentication authentication) {
        // Assuming the authentication principal contains user ID
        // Adjust based on your security configuration
        return Long.valueOf(authentication.getName());
    }


    private User getCurrentUser() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        return userService.findByUsername(username).orElseThrow();
    }


    // ==================== CALL INITIATION ====================

    /**
     * Initiate a direct call (1-on-1)
     * POST /api/calls/direct
     */
    @PostMapping("/direct")
    public ResponseEntity<CallDTO> initiateDirectCall(
            @RequestBody Map<String, Object> request,
            Authentication authentication) {
        try {
            Long callerId = getCurrentUserId(authentication);
            Long conversationId = Long.valueOf(request.get("conversationId").toString());
            String callTypeStr = request.get("callType").toString();
            Call.CallType callType = Call.CallType.valueOf(callTypeStr);

            // Check if user is already in a call
            if (callService.isUserInActiveCall(callerId)) {
                return ResponseEntity.badRequest().build();
            }

            Call call = callService.initiateDirectCall(callerId, conversationId, callType);
            return ResponseEntity.ok(CallDTO.fromEntity(call));

        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Initiate a group call
     * POST /api/calls/group
     */
    @PostMapping("/group")
    public ResponseEntity<CallDTO> initiateGroupCall(
            @RequestBody Map<String, Object> request,
            Authentication authentication) {
        try {
            Long callerId = getCurrentUserId(authentication);
            Long chatRoomId = Long.valueOf(request.get("chatRoomId").toString());
            String callTypeStr = request.get("callType").toString();
            Call.CallType callType = Call.CallType.valueOf(callTypeStr);

            // Check if user is already in a call
            if (callService.isUserInActiveCall(callerId)) {
                return ResponseEntity.badRequest().build();
            }

            Call call = callService.initiateGroupCall(callerId, chatRoomId, callType);
            return ResponseEntity.ok(CallDTO.fromEntity(call));

        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    // ==================== CALL ACTIONS ====================

    /**
     * Accept an incoming call
     * POST /api/calls/{callId}/accept
     */
    @PostMapping("/{callId}/accept")
    public ResponseEntity<CallDTO> acceptCall(
            @PathVariable Long callId,
            Authentication authentication) {
        try {
            Long userId = getCurrentUserId(authentication);
            Call call = callService.acceptCall(callId, userId);
            return ResponseEntity.ok(CallDTO.fromEntity(call));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Reject an incoming call
     * POST /api/calls/{callId}/reject
     */
    @PostMapping("/{callId}/reject")
    public ResponseEntity<CallDTO> rejectCall(
            @PathVariable Long callId,
            Authentication authentication) {
        try {
            Long userId = getCurrentUserId(authentication);
            Call call = callService.rejectCall(callId, userId);
            return ResponseEntity.ok(CallDTO.fromEntity(call));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * End a call
     * POST /api/calls/{callId}/end
     */
    @PostMapping("/{callId}/end")
    public ResponseEntity<CallDTO> endCall(
            @PathVariable Long callId,
            Authentication authentication) {
        try {
            Long userId = getCurrentUserId(authentication);
            Call call = callService.endCall(callId, userId);
            return ResponseEntity.ok(CallDTO.fromEntity(call));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Cancel a call (before it's answered)
     * POST /api/calls/{callId}/cancel
     */
    @PostMapping("/{callId}/cancel")
    public ResponseEntity<CallDTO> cancelCall(
            @PathVariable Long callId,
            Authentication authentication) {
        try {
            Long userId = getCurrentUserId(authentication);
            Call call = callService.cancelCall(callId, userId);
            return ResponseEntity.ok(CallDTO.fromEntity(call));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    // ==================== CALL RETRIEVAL ====================

    /**
     * Get call details by ID
     * GET /api/calls/{callId}
     */
    @GetMapping("/{callId}")
    public ResponseEntity<CallDTO> getCallById(@PathVariable Long callId) {
        try {
            Call call = callService.findById(callId)
                    .orElseThrow(() -> new RuntimeException("Call not found"));
            return ResponseEntity.ok(CallDTO.fromEntity(call));
        } catch (RuntimeException e) {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Get call history for current user
     * GET /api/calls/history?limit=20
     */
    @GetMapping("/history")
    public ResponseEntity<List<CallDTO>> getCallHistory(@RequestParam(defaultValue = "50") int limit) {
        User currentUser = getCurrentUser();
        List<Call> calls = callService.getUserCallHistory(currentUser.getId(), limit);
        List<CallDTO> callDTOs = calls.stream()
                .map(CallDTO::fromEntity)
                .collect(Collectors.toList());
        return ResponseEntity.ok(callDTOs);
    }
    

    /**
     * Get call history for a conversation
     * GET /api/calls/conversation/{conversationId}
     */
    @GetMapping("/conversation/{conversationId}")
    public ResponseEntity<List<CallDTO>> getConversationCalls(
            @PathVariable Long conversationId) {
        try {
            List<Call> calls = callService.getConversationCalls(conversationId);
            List<CallDTO> callDTOs = calls.stream()
                    .map(CallDTO::fromEntity)
                    .collect(Collectors.toList());
            return ResponseEntity.ok(callDTOs);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Get call history for a chat room
     * GET /api/calls/chatroom/{chatRoomId}
     */
    @GetMapping("/chatroom/{chatRoomId}")
    public ResponseEntity<List<CallDTO>> getChatRoomCalls(
            @PathVariable Long chatRoomId) {
        try {
            List<Call> calls = callService.getChatRoomCalls(chatRoomId);
            List<CallDTO> callDTOs = calls.stream()
                    .map(CallDTO::fromEntity)
                    .collect(Collectors.toList());
            return ResponseEntity.ok(callDTOs);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    // ==================== ACTIVE CALL STATUS ====================

    /**
     * Check if user is currently in an active call
     * GET /api/calls/active/status
     */
    @GetMapping("/active/status")
    public ResponseEntity<Map<String, Boolean>> getActiveCallStatus(
            Authentication authentication) {
        try {
            Long userId = getCurrentUserId(authentication);
            boolean inCall = callService.isUserInActiveCall(userId);
            return ResponseEntity.ok(Map.of("inActiveCall", inCall));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Get active call for a conversation (if any)
     * GET /api/calls/conversation/{conversationId}/active
     */
    @GetMapping("/conversation/{conversationId}/active")
    public ResponseEntity<CallDTO> getActiveConversationCall(
            @PathVariable Long conversationId) {
        try {
            Call call = callService.getActiveCallForConversation(conversationId)
                    .orElse(null);
            if (call != null) {
                return ResponseEntity.ok(CallDTO.fromEntity(call));
            }
            return ResponseEntity.notFound().build();
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Get active call for a chat room (if any)
     * GET /api/calls/chatroom/{chatRoomId}/active
     */
    @GetMapping("/chatroom/{chatRoomId}/active")
    public ResponseEntity<CallDTO> getActiveChatRoomCall(
            @PathVariable Long chatRoomId) {
        try {
            Call call = callService.getActiveCallForChatRoom(chatRoomId)
                    .orElse(null);
            if (call != null) {
                return ResponseEntity.ok(CallDTO.fromEntity(call));
            }
            return ResponseEntity.notFound().build();
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().build();
        }
    }


    @GetMapping("/check-availability/{userId}")
    public ResponseEntity<Boolean> checkAvailability(@PathVariable Long userId) {
        User user = userService.findById(userId).orElseThrow();
        // User is available if ONLINE and not currently in a call status
        return ResponseEntity.ok(user.getStatus() == User.UserStatus.ONLINE);
    }

    
    // ==================== STATISTICS ====================

    /**
     * Get call statistics for current user
     * GET /api/calls/statistics
     */
    @GetMapping("/statistics")
    public ResponseEntity<Map<String, Object>> getCallStatistics(
            Authentication authentication) {
        try {
            Long userId = getCurrentUserId(authentication);
            Map<String, Object> stats = callService.getCallStatistics(userId);
            return ResponseEntity.ok(stats);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Cleanup missed calls (Admin only - manual trigger)
     * POST /api/calls/cleanup/missed
     */
    @PostMapping("/cleanup/missed")
    public ResponseEntity<String> cleanupMissedCalls() {
        try {
            callService.markMissedCalls();
            return ResponseEntity.ok("Missed calls marked successfully");
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().build();
        }
    }
}