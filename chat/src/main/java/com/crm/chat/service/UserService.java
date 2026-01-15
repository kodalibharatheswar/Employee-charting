package com.crm.chat.service;

import com.crm.chat.entity.User;
import com.crm.chat.entity.User.UserStatus;
import com.crm.chat.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * User Service with enhanced status management
 * Supports 8 status types: Available, Away, Busy, Invisible, DND, Engaged, Available for Collab, In Meeting
 */
@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    // ============================================
    // USER REGISTRATION & AUTHENTICATION
    // ============================================

    /**
     * Register a new user with default AVAILABLE status
     */
    public User registerUser(User user) {
        if (userRepository.existsByUsername(user.getUsername())) {
            throw new RuntimeException("Username already exists");
        }
        if (userRepository.existsByEmail(user.getEmail())) {
            throw new RuntimeException("Email already exists");
        }

        user.setPassword(passwordEncoder.encode(user.getPassword()));
        user.setActive(true);
        user.setStatus(UserStatus.AVAILABLE); // Changed from OFFLINE to AVAILABLE
        user.setStatusUpdatedAt(LocalDateTime.now());
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());

        log.info("Registering new user: {} with status: {}", user.getUsername(), user.getStatus());
        return userRepository.save(user);
    }

    /**
     * Find user by username
     */
    @Transactional(readOnly = true)
    public Optional<User> findByUsername(String username) {
        return userRepository.findByUsername(username);
    }

    /**
     * Find user by ID
     */
    @Transactional(readOnly = true)
    public Optional<User> findById(Long id) {
        return userRepository.findById(id);
    }

    /**
     * Validate user password
     */
    @Transactional(readOnly = true)
    public boolean validatePassword(User user, String rawPassword) {
        return passwordEncoder.matches(rawPassword, user.getPassword());
    }

    /**
     * Update user details
     */
    public User updateUser(User user) {
        user.setUpdatedAt(LocalDateTime.now());
        return userRepository.save(user);
    }

    // ============================================
    // USER LISTING & SEARCH
    // ============================================

    /**
     * Get all active users
     */
    @Transactional(readOnly = true)
    public List<User> getAllActiveUsers() {
        return userRepository.findByActiveTrue();
    }

    /**
     * Get all active users except the current user
     */
    @Transactional(readOnly = true)
    public List<User> getAllActiveUsersExcept(Long currentUserId) {
        return userRepository.findAllActiveUsersExcept(currentUserId);
    }

    /**
     * Get all active users with their status, excluding current user
     */
    @Transactional(readOnly = true)
    public List<User> getAllActiveUsersWithStatus(Long currentUserId) {
        return userRepository.findAllActiveUsersExcept(currentUserId);
    }

    /**
     * Search users by username or full name
     */
    @Transactional(readOnly = true)
    public List<User> searchUsers(String searchTerm, Long currentUserId) {
        return userRepository.searchUsers(searchTerm, currentUserId);
    }

    /**
     * Get visible users (not invisible)
     */
    @Transactional(readOnly = true)
    public List<User> getVisibleUsers(Long currentUserId) {
        return userRepository.findAllActiveUsersExcept(currentUserId)
            .stream()
            .filter(User::isVisible)
            .collect(Collectors.toList());
    }

    // ============================================
    // BASIC STATUS MANAGEMENT (Legacy Methods)
    // ============================================

    /**
     * Update user status (generic method)
     */
    public void updateUserStatus(Long userId, UserStatus status) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found with id: " + userId));
        
        user.setStatus(status);
        user.setLastSeen(LocalDateTime.now());
        
        log.info("Updated user {} status to: {}", user.getUsername(), status);
        userRepository.save(user);
    }

    /**
     * Update user status by string value (for API calls)
     */
    public void updateUserStatus(Long userId, String statusString) {
        try {
            UserStatus status = UserStatus.valueOf(statusString);
            updateUserStatus(userId, status);
        } catch (IllegalArgumentException e) {
            throw new RuntimeException("Invalid status: " + statusString);
        }
    }

    /**
     * Set user online (maps to AVAILABLE)
     */
    public void setUserOnline(Long userId) {
        updateUserStatus(userId, UserStatus.AVAILABLE); // Changed from ONLINE to AVAILABLE
        log.info("User {} set to AVAILABLE (online)", userId);
    }

    /**
     * Set user offline
     */
    public void setUserOffline(Long userId) {
        updateUserStatus(userId, UserStatus.OFFLINE);
        log.info("User {} set to OFFLINE", userId);
    }

    /**
     * Get online users (includes AVAILABLE and AVAILABLE_COLLAB)
     */
    @Transactional(readOnly = true)
    public List<User> getOnlineUsers() {
        return userRepository.findByActiveTrue()
            .stream()
            .filter(User::isOnline)
            .collect(Collectors.toList());
    }

    // ============================================
    // ENHANCED STATUS MANAGEMENT (New Methods)
    // ============================================

    /**
     * Set user as available
     */
    public void setUserAvailable(Long userId) {
        updateUserStatus(userId, UserStatus.AVAILABLE);
        log.info("User {} set to AVAILABLE", userId);
    }

    /**
     * Set user as away
     */
    public void setUserAway(Long userId) {
        updateUserStatus(userId, UserStatus.AWAY);
        log.info("User {} set to AWAY", userId);
    }

    /**
     * Set user as busy
     */
    public void setUserBusy(Long userId) {
        updateUserStatus(userId, UserStatus.BUSY);
        log.info("User {} set to BUSY", userId);
    }

    /**
     * Set user as invisible
     */
    public void setUserInvisible(Long userId) {
        updateUserStatus(userId, UserStatus.INVISIBLE);
        log.info("User {} set to INVISIBLE", userId);
    }

    /**
     * Set user as Do Not Disturb
     */
    public void setUserDND(Long userId) {
        updateUserStatus(userId, UserStatus.DND);
        log.info("User {} set to DND", userId);
    }

    /**
     * Set user as engaged at work
     */
    public void setUserEngaged(Long userId) {
        updateUserStatus(userId, UserStatus.ENGAGED);
        log.info("User {} set to ENGAGED", userId);
    }

    /**
     * Set user as available for collaboration
     */
    public void setUserAvailableForCollaboration(Long userId) {
        updateUserStatus(userId, UserStatus.AVAILABLE_COLLAB);
        log.info("User {} set to AVAILABLE_COLLAB", userId);
    }

    /**
     * Set user as in a meeting
     */
    public void setUserInMeeting(Long userId) {
        updateUserStatus(userId, UserStatus.IN_MEETING);
        log.info("User {} set to IN_MEETING", userId);
    }

    // ============================================
    // STATUS QUERY METHODS
    // ============================================

    /**
     * Get user's current status
     */
    @Transactional(readOnly = true)
    public UserStatus getUserStatus(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found with id: " + userId));
        return user.getStatus();
    }

    /**
     * Get user's status display text
     */
    @Transactional(readOnly = true)
    public String getUserStatusText(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found with id: " + userId));
        return user.getStatusText();
    }

    /**
     * Check if user is available for chat
     */
    @Transactional(readOnly = true)
    public boolean isUserAvailable(Long userId) {
        return userRepository.findById(userId)
                .map(User::isAvailable)
                .orElse(false);
    }

    /**
     * Check if user is in Do Not Disturb mode
     */
    @Transactional(readOnly = true)
    public boolean isUserDND(Long userId) {
        return userRepository.findById(userId)
                .map(User::isDND)
                .orElse(false);
    }

    /**
     * Check if user is visible to others
     */
    @Transactional(readOnly = true)
    public boolean isUserVisible(Long userId) {
        return userRepository.findById(userId)
                .map(User::isVisible)
                .orElse(false);
    }

    /**
     * Check if user is online (visible and active)
     */
    @Transactional(readOnly = true)
    public boolean isUserOnline(Long userId) {
        return userRepository.findById(userId)
                .map(User::isOnline)
                .orElse(false);
    }

    // ============================================
    // STATUS FILTERING METHODS
    // ============================================

    /**
     * Get all available users (AVAILABLE or AVAILABLE_COLLAB)
     */
    @Transactional(readOnly = true)
    public List<User> getAvailableUsers() {
        return userRepository.findByActiveTrue()
                .stream()
                .filter(User::isAvailable)
                .collect(Collectors.toList());
    }

    /**
     * Get all busy users (BUSY, ENGAGED, DND, IN_MEETING)
     */
    @Transactional(readOnly = true)
    public List<User> getBusyUsers() {
        return userRepository.findByActiveTrue()
                .stream()
                .filter(user -> {
                    UserStatus status = user.getStatus();
                    return status == UserStatus.BUSY || 
                           status == UserStatus.ENGAGED ||
                           status == UserStatus.DND || 
                           status == UserStatus.IN_MEETING;
                })
                .collect(Collectors.toList());
    }

    /**
     * Get users by specific status
     */
    @Transactional(readOnly = true)
    public List<User> getUsersByStatus(UserStatus status) {
        return userRepository.findByStatus(status);
    }

    /**
     * Get users by status string (for API calls)
     */
    @Transactional(readOnly = true)
    public List<User> getUsersByStatus(String statusString) {
        try {
            UserStatus status = UserStatus.valueOf(statusString);
            return getUsersByStatus(status);
        } catch (IllegalArgumentException e) {
            throw new RuntimeException("Invalid status: " + statusString);
        }
    }

    /**
     * Get all users excluding invisible ones
     */
    @Transactional(readOnly = true)
    public List<User> getAllVisibleUsers(Long currentUserId) {
        return userRepository.findAllActiveUsersExcept(currentUserId)
                .stream()
                .filter(user -> user.getStatus() != UserStatus.INVISIBLE)
                .collect(Collectors.toList());
    }

    // ============================================
    // BATCH STATUS OPERATIONS
    // ============================================

    /**
     * Update status for multiple users
     */
    public void updateMultipleUserStatuses(List<Long> userIds, UserStatus status) {
        userIds.forEach(userId -> {
            try {
                updateUserStatus(userId, status);
            } catch (Exception e) {
                log.error("Failed to update status for user {}: {}", userId, e.getMessage());
            }
        });
    }

    /**
     * Set all users offline (for system maintenance)
     */
    public void setAllUsersOffline() {
        List<User> allUsers = userRepository.findAll();
        allUsers.forEach(user -> {
            user.setStatus(UserStatus.OFFLINE);
            user.setLastSeen(LocalDateTime.now());
        });
        userRepository.saveAll(allUsers);
        log.info("Set all users to OFFLINE");
    }

    // ============================================
    // STATUS STATISTICS
    // ============================================

    /**
     * Get count of users by status
     */
    @Transactional(readOnly = true)
    public long countUsersByStatus(UserStatus status) {
        return userRepository.findByStatus(status).size();
    }

    /**
     * Get status distribution (for analytics)
     */
    @Transactional(readOnly = true)
    public java.util.Map<UserStatus, Long> getStatusDistribution() {
        List<User> allUsers = userRepository.findByActiveTrue();
        return allUsers.stream()
                .collect(Collectors.groupingBy(User::getStatus, Collectors.counting()));
    }

    // ============================================
    // HELPER METHODS
    // ============================================

    /**
     * Validate status string
     */
    public boolean isValidStatus(String statusString) {
        try {
            UserStatus.valueOf(statusString);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /**
     * Get all available status options
     */
    public List<String> getAllStatusOptions() {
        return java.util.Arrays.stream(UserStatus.values())
                .map(UserStatus::name)
                .collect(Collectors.toList());
    }

    /**
     * Get all status options with display text
     */
    public java.util.Map<String, String> getAllStatusOptionsWithText() {
        return java.util.Arrays.stream(UserStatus.values())
                .collect(Collectors.toMap(
                    UserStatus::name,
                    UserStatus::getDisplayText
                ));
    }

    // ============================================
    // AUTO STATUS UPDATES (Optional)
    // ============================================

    /**
     * Auto-set users to AWAY if inactive for specified minutes
     * Call this from a scheduled task
     */
    public void autoSetInactiveUsersToAway(int inactiveMinutes) {
        LocalDateTime cutoffTime = LocalDateTime.now().minusMinutes(inactiveMinutes);
        
        List<User> activeUsers = userRepository.findByActiveTrue()
                .stream()
                .filter(user -> user.getStatus() == UserStatus.AVAILABLE || 
                               user.getStatus() == UserStatus.AVAILABLE_COLLAB)
                .filter(user -> user.getLastSeen() != null && 
                               user.getLastSeen().isBefore(cutoffTime))
                .collect(Collectors.toList());
        
        activeUsers.forEach(user -> {
            user.setStatus(UserStatus.AWAY);
            log.info("Auto-set user {} to AWAY due to inactivity", user.getUsername());
        });
        
        if (!activeUsers.isEmpty()) {
            userRepository.saveAll(activeUsers);
            log.info("Auto-set {} users to AWAY due to inactivity", activeUsers.size());
        }
    }

    /**
     * Update user's last seen timestamp
     */
    public void updateLastSeen(Long userId) {
        userRepository.findById(userId).ifPresent(user -> {
            user.setLastSeen(LocalDateTime.now());
            userRepository.save(user);
        });
    }
}