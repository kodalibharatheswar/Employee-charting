package com.crm.chat.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.Objects;

@Entity
@Table(name = "calls", indexes = {
        @Index(name = "idx_call_conversation", columnList = "conversation_id"),
        @Index(name = "idx_call_chatroom", columnList = "chat_room_id"),
        @Index(name = "idx_call_created", columnList = "created_at"),
        @Index(name = "idx_call_caller", columnList = "caller_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Call {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CallType callType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private CallMode callMode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "conversation_id")
    private Conversation conversation;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "chat_room_id")
    private ChatRoom chatRoom;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "caller_id", nullable = false)
    private User caller;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CallStatus status = CallStatus.INITIATED;

    @Column
    private LocalDateTime startedAt;

    @Column
    private LocalDateTime endedAt;

    @Column
    private Integer duration; // Duration in seconds

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @OneToMany(mappedBy = "call", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<CallParticipant> participants = new HashSet<>();

    @Column(length = 100)
    private String roomId; // WebRTC room/session ID

    @Column(columnDefinition = "TEXT")
    private String metadata; // Additional JSON metadata (e.g., screen share settings, quality)

    /**
     * Call Types
     */
    public enum CallType {
        AUDIO,          // Voice call only
        VIDEO,          // Video call
        SCREEN_SHARE    // Screen sharing session
    }

    /**
     * Call Modes
     */
    public enum CallMode {
        DIRECT,   // 1-on-1 call
        GROUP     // Group/conference call
    }

    /**
     * Call Status
     */
    public enum CallStatus {
        INITIATED,  // Call has been initiated
        RINGING,    // Ringing on recipient's end
        ONGOING,    // Call is currently active
        ENDED,      // Call ended normally
        MISSED,     // Call was not answered
        REJECTED,   // Call was rejected by recipient
        FAILED      // Call failed due to technical issues
    }

    // Helper methods

    /**
     * Check if call is for direct conversation
     */
    public boolean isDirectCall() {
        return callMode == CallMode.DIRECT;
    }

    /**
     * Check if call is for group chat
     */
    public boolean isGroupCall() {
        return callMode == CallMode.GROUP;
    }

    /**
     * Check if call is currently active
     */
    public boolean isActive() {
        return status == CallStatus.ONGOING || status == CallStatus.RINGING;
    }

    /**
     * Check if call has ended
     */
    public boolean hasEnded() {
        return status == CallStatus.ENDED || 
               status == CallStatus.MISSED || 
               status == CallStatus.REJECTED || 
               status == CallStatus.FAILED;
    }

    /**
     * Start the call
     */
    public void start() {
        this.status = CallStatus.ONGOING;
        this.startedAt = LocalDateTime.now();
    }

    /**
     * End the call
     */
    public void end() {
        this.status = CallStatus.ENDED;
        this.endedAt = LocalDateTime.now();
        if (this.startedAt != null) {
            this.duration = (int) java.time.Duration.between(this.startedAt, this.endedAt).getSeconds();
        }
    }

    /**
     * Mark call as missed
     */
    public void markAsMissed() {
        this.status = CallStatus.MISSED;
        this.endedAt = LocalDateTime.now();
    }

    /**
     * Mark call as rejected
     */
    public void reject() {
        this.status = CallStatus.REJECTED;
        this.endedAt = LocalDateTime.now();
    }

    /**
     * Mark call as failed
     */
    public void fail() {
        this.status = CallStatus.FAILED;
        this.endedAt = LocalDateTime.now();
    }

    /**
     * Set status to ringing
     */
    public void setRinging() {
        this.status = CallStatus.RINGING;
    }

    /**
     * Add a participant to the call
     */
    public void addParticipant(CallParticipant participant) {
        this.participants.add(participant);
        participant.setCall(this);
    }

    /**
     * Remove a participant from the call
     */
    public void removeParticipant(CallParticipant participant) {
        this.participants.remove(participant);
        participant.setCall(null);
    }

    /**
     * Get count of participants currently in the call
     */
    public long getActiveParticipantCount() {
        return participants.stream()
                .filter(p -> p.getStatus() == CallParticipant.ParticipantStatus.JOINED)
                .count();
    }

    /**
     * Generate a unique room ID for WebRTC
     */
    public static String generateRoomId() {
        return "room_" + System.currentTimeMillis() + "_" + 
               (int)(Math.random() * 10000);
    }

    /**
     * Get formatted duration string
     */
    public String getFormattedDuration() {
        if (duration == null || duration == 0) return "0:00";
        int minutes = duration / 60;
        int seconds = duration % 60;
        return String.format("%d:%02d", minutes, seconds);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Call call)) return false;
        return id != null && id.equals(call.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "Call{" +
                "id=" + id +
                ", callType=" + callType +
                ", callMode=" + callMode +
                ", status=" + status +
                ", caller=" + (caller != null ? caller.getUsername() : "null") +
                ", duration=" + getFormattedDuration() +
                '}';
    }
}