package com.crm.chat.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.Objects;

/**
 * User entity with complete status management
 * Supports 8 status types: Available, Away, Busy, Invisible, DND, Engaged, Available for Collab, In Meeting
 */
@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String username;

    @Column(nullable = false, unique = true, length = 100)
    private String email;

    @Column(nullable = false)
    private String password;

    @Column(nullable = false, length = 100)
    private String fullName;

    @Column(length = 50)
    private String department;

    @Column(length = 50)
    private String designation;

    /**
     * User status with expanded options for better presence management
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private UserStatus status = UserStatus.AVAILABLE;

    /**
     * Timestamp when status was last updated
     */
    @Column(name = "status_updated_at")
    private LocalDateTime statusUpdatedAt;

    /**
     * Last time user was seen online
     */
    @Column
    private LocalDateTime lastSeen;

    /**
     * Whether user account is active
     */
    @Column(nullable = false)
    private Boolean active = true;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "sender", cascade = CascadeType.ALL)
    private Set<Message> sentMessages = new HashSet<>();

    @ManyToMany(mappedBy = "participants")
    private Set<Conversation> conversations = new HashSet<>();

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL)
    private Set<ChatRoomMember> chatRoomMemberships = new HashSet<>();

    /**
     * Enhanced UserStatus enum with 8 status options
     * Matches frontend status management dropdown
     */
    public enum UserStatus {
        /**
         * Default Status Options
         */
        AVAILABLE("Available"),           // Green dot - Ready to chat
        AWAY("Away"),                     // Orange dot - Stepped away
        BUSY("Busy"),                     // Red dot - Working, less available
        INVISIBLE("Invisible"),           // Gray dot - Appear offline
        DND("Do not disturb"),           // Dark red dot - No interruptions
        
        /**
         * Custom Status Options
         */
        ENGAGED("Engaged at work"),      // Red dot - Focused work
        AVAILABLE_COLLAB("Available for Collaboration"), // Green dot - Open to discuss
        IN_MEETING("In a Meeting"),      // Dark red dot - In meeting
        
        /**
         * Legacy Status (for backward compatibility)
         */
        ONLINE("Online"),                 // Equivalent to AVAILABLE
        OFFLINE("Offline");               // User is offline

        private final String displayText;

        UserStatus(String displayText) {
            this.displayText = displayText;
        }

        public String getDisplayText() {
            return displayText;
        }

        /**
         * Get color indicator for this status
         */
        public String getColorIndicator() {
            return switch (this) {
                case AVAILABLE, AVAILABLE_COLLAB, ONLINE -> "green";
                case AWAY -> "orange";
                case BUSY, ENGAGED -> "red";
                case DND, IN_MEETING -> "dark-red";
                case INVISIBLE, OFFLINE -> "gray";
            };
        }

        /**
         * Check if user is available for chat
         */
        public boolean isAvailable() {
            return this == AVAILABLE || this == AVAILABLE_COLLAB || this == ONLINE;
        }

        /**
         * Check if user should not be disturbed
         */
        public boolean isDND() {
            return this == DND || this == IN_MEETING;
        }

        /**
         * Check if user is visible to others
         */
        public boolean isVisible() {
            return this != INVISIBLE && this != OFFLINE;
        }
    }

    // ============================================
    // Status Management Helper Methods
    // ============================================

    /**
     * Set user status and update timestamp
     */
    public void setStatus(UserStatus status) {
        this.status = status;
        this.statusUpdatedAt = LocalDateTime.now();
        if (status.isVisible()) {
            this.lastSeen = LocalDateTime.now();
        }
    }

    /**
     * Get current status or default to AVAILABLE
     */
    public UserStatus getStatus() {
        return this.status != null ? this.status : UserStatus.AVAILABLE;
    }

    /**
     * Set user as available
     */
    public void setAvailable() {
        setStatus(UserStatus.AVAILABLE);
    }

    /**
     * Set user as away
     */
    public void setAway() {
        setStatus(UserStatus.AWAY);
    }

    /**
     * Set user as busy
     */
    public void setBusy() {
        setStatus(UserStatus.BUSY);
    }

    /**
     * Set user as invisible (appear offline)
     */
    public void setInvisible() {
        setStatus(UserStatus.INVISIBLE);
    }

    /**
     * Set user as Do Not Disturb
     */
    public void setDND() {
        setStatus(UserStatus.DND);
    }

    /**
     * Set user as engaged at work
     */
    public void setEngaged() {
        setStatus(UserStatus.ENGAGED);
    }

    /**
     * Set user as available for collaboration
     */
    public void setAvailableForCollaboration() {
        setStatus(UserStatus.AVAILABLE_COLLAB);
    }

    /**
     * Set user as in a meeting
     */
    public void setInMeeting() {
        setStatus(UserStatus.IN_MEETING);
    }

    /**
     * Set user online (legacy method - maps to AVAILABLE)
     */
    public void setOnline() {
        setStatus(UserStatus.AVAILABLE);
    }

    /**
     * Set user offline
     */
    public void setOffline() {
        setStatus(UserStatus.OFFLINE);
    }

    /**
     * Check if user is online/available
     */
    public boolean isOnline() {
        return this.status != null && this.status.isVisible();
    }

    /**
     * Check if user is available for chat
     */
    public boolean isAvailable() {
        return this.status != null && this.status.isAvailable();
    }

    /**
     * Check if user should not be disturbed
     */
    public boolean isDND() {
        return this.status != null && this.status.isDND();
    }

    /**
     * Check if user is visible to others
     */
    public boolean isVisible() {
        return this.status != null && this.status.isVisible();
    }

    /**
     * Get status display text
     */
    public String getStatusText() {
        return this.status != null ? this.status.getDisplayText() : "Available";
    }

    /**
     * Get status color indicator
     */
    public String getStatusColor() {
        return this.status != null ? this.status.getColorIndicator() : "green";
    }

    // ============================================
    // Equals and HashCode
    // ============================================

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof User user)) return false;
        return id != null && id.equals(user.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    // ============================================
    // ToString (for debugging)
    // ============================================

    @Override
    public String toString() {
        return "User{" +
                "id=" + id +
                ", username='" + username + '\'' +
                ", fullName='" + fullName + '\'' +
                ", status=" + status +
                ", department='" + department + '\'' +
                '}';
    }
}