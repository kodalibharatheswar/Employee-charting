package com.crm.chat.controller;

import com.crm.chat.entity.User;
import com.crm.chat.entity.User.UserStatus;
import com.crm.chat.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * REST API Controller for User Status Management
 * Handles status changes and broadcasts updates to connected users
 */
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class UserStatusController {

    private final UserService userService;
    private final SimpMessagingTemplate messagingTemplate;

    /**
     * Get current authenticated user
     */
    private User getCurrentUser() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        return userService.findByUsername(username)
            .orElseThrow(() -> new RuntimeException("User not found"));
    }

    /**
     * Update user status
     * POST /api/users/status
     * Body: { "status": "AVAILABLE" }
     */
    @PostMapping("/status")
    public ResponseEntity<Map<String, Object>> updateStatus(@RequestBody Map<String, String> request) {
        try {
            User currentUser = getCurrentUser();
            String statusString = request.get("status");
            
            if (statusString == null || statusString.isEmpty()) {
                return ResponseEntity.badRequest().body(createErrorResponse("Status is required"));
            }
            
            // Convert String to UserStatus enum
            UserStatus newStatus;
            try {
                newStatus = UserStatus.valueOf(statusString);
            } catch (IllegalArgumentException e) {
                return ResponseEntity.badRequest().body(createErrorResponse("Invalid status: " + statusString));
            }
            
            // Update user status in database using UserService
            userService.updateUserStatus(currentUser.getId(), newStatus);
            
            // Get display text for status
            String statusText = getStatusText(statusString);
            
            // Broadcast status change to all connected users via WebSocket
            Map<String, Object> statusUpdate = new HashMap<>();
            statusUpdate.put("userId", currentUser.getId());
            statusUpdate.put("username", currentUser.getUsername());
            statusUpdate.put("fullName", currentUser.getFullName());
            statusUpdate.put("status", statusString);
            statusUpdate.put("statusText", statusText);
            
            // Fix: Cast to Object to resolve ambiguity
            messagingTemplate.convertAndSend("/topic/user.status", (Object) statusUpdate);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Status updated successfully");
            response.put("status", statusString);
            response.put("statusText", statusText);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(createErrorResponse("Failed to update status: " + e.getMessage()));
        }
    }

    /**
     * Get current user's status
     * GET /api/users/status
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getStatus() {
        try {
            User currentUser = getCurrentUser();
            UserStatus userStatus = currentUser.getStatus();
            String statusString = userStatus.name(); // Convert enum to String
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("userId", currentUser.getId());
            response.put("status", statusString);
            response.put("statusText", getStatusText(statusString));
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(createErrorResponse("Failed to get status: " + e.getMessage()));
        }
    }

    /**
     * Get status of a specific user
     * GET /api/users/{userId}/status
     */
    @GetMapping("/{userId}/status")
    public ResponseEntity<Map<String, Object>> getUserStatus(@PathVariable Long userId) {
        try {
            User user = userService.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));
            
            UserStatus userStatus = user.getStatus();
            String statusString = userStatus.name(); // Convert enum to String
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("userId", user.getId());
            response.put("username", user.getUsername());
            response.put("fullName", user.getFullName());
            response.put("status", statusString);
            response.put("statusText", getStatusText(statusString));
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(createErrorResponse("Failed to get user status: " + e.getMessage()));
        }
    }

    /**
     * Get all users with their current status
     * GET /api/users/all-with-status
     */
    @GetMapping("/all-with-status")
    public ResponseEntity<Map<String, Object>> getAllUsersWithStatus() {
        try {
            User currentUser = getCurrentUser();
            List<User> users = userService.getAllActiveUsersExcept(currentUser.getId());
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("users", users);
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(createErrorResponse("Failed to get users: " + e.getMessage()));
        }
    }

    /**
     * Convert status code to display text
     */
    private String getStatusText(String status) {
        return switch (status) {
            case "AVAILABLE" -> "Available";
            case "AWAY" -> "Away";
            case "BUSY" -> "Busy";
            case "INVISIBLE" -> "Invisible";
            case "DND" -> "Do not disturb";
            case "ENGAGED" -> "Engaged at work";
            case "AVAILABLE_COLLAB" -> "Available for Collaboration";
            case "IN_MEETING" -> "In a Meeting";
            case "ONLINE" -> "Online";
            case "OFFLINE" -> "Offline";
            default -> "Available";
        };
    }

    /**
     * Create error response map
     */
    private Map<String, Object> createErrorResponse(String message) {
        Map<String, Object> error = new HashMap<>();
        error.put("success", false);
        error.put("message", message);
        return error;
    }
}