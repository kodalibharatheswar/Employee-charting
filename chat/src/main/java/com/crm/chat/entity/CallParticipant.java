package com.crm.chat.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.Objects;

@Entity
@Table(name = "call_participants", indexes = {
        @Index(name = "idx_participant_call", columnList = "call_id"),
        @Index(name = "idx_participant_user", columnList = "user_id"),
        @Index(name = "idx_participant_call_user", columnList = "call_id,user_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CallParticipant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "call_id", nullable = false)
    private Call call;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ParticipantStatus status = ParticipantStatus.INVITED;

    @Column
    private LocalDateTime joinedAt;

    @Column
    private LocalDateTime leftAt;

    @Column(nullable = false)
    private Boolean microphoneEnabled = true;

    @Column(nullable = false)
    private Boolean cameraEnabled = true;

    @Column(nullable = false)
    private Boolean screenSharing = false;

    @Column(length = 100)
    private String peerId; // WebRTC peer connection ID

    /**
     * Participant Status in Call
     */
    public enum ParticipantStatus {
        INVITED,    // Participant has been invited but not joined
        RINGING,    // Call is ringing on participant's device
        JOINED,     // Participant has joined the call
        LEFT,       // Participant has left the call
        REJECTED    // Participant rejected the call
    }

    // Helper methods

    /**
     * Check if participant is currently in the call
     */
    public boolean isActive() {
        return status == ParticipantStatus.JOINED;
    }

    /**
     * Join the call
     */
    public void join() {
        this.status = ParticipantStatus.JOINED;
        this.joinedAt = LocalDateTime.now();
    }

    /**
     * Leave the call
     */
    public void leave() {
        this.status = ParticipantStatus.LEFT;
        this.leftAt = LocalDateTime.now();
    }

    /**
     * Reject the call invitation
     */
    public void reject() {
        this.status = ParticipantStatus.REJECTED;
        this.leftAt = LocalDateTime.now();
    }

    /**
     * Set status to ringing
     */
    public void setRinging() {
        this.status = ParticipantStatus.RINGING;
    }

    /**
     * Toggle microphone state
     */
    public void toggleMicrophone() {
        this.microphoneEnabled = !this.microphoneEnabled;
    }

    /**
     * Toggle camera state
     */
    public void toggleCamera() {
        this.cameraEnabled = !this.cameraEnabled;
    }

    /**
     * Toggle screen sharing
     */
    public void toggleScreenShare() {
        this.screenSharing = !this.screenSharing;
    }

    /**
     * Mute microphone
     */
    public void muteMicrophone() {
        this.microphoneEnabled = false;
    }

    /**
     * Unmute microphone
     */
    public void unmuteMicrophone() {
        this.microphoneEnabled = true;
    }

    /**
     * Disable camera
     */
    public void disableCamera() {
        this.cameraEnabled = false;
    }

    /**
     * Enable camera
     */
    public void enableCamera() {
        this.cameraEnabled = true;
    }

    /**
     * Start screen sharing
     */
    public void startScreenShare() {
        this.screenSharing = true;
    }

    /**
     * Stop screen sharing
     */
    public void stopScreenShare() {
        this.screenSharing = false;
    }

    /**
     * Get duration of participation in seconds
     */
    public Integer getDuration() {
        if (joinedAt == null) return 0;
        LocalDateTime endTime = leftAt != null ? leftAt : LocalDateTime.now();
        return (int) java.time.Duration.between(joinedAt, endTime).getSeconds();
    }

    /**
     * Get formatted duration string
     */
    public String getFormattedDuration() {
        Integer duration = getDuration();
        if (duration == null || duration == 0) return "0:00";
        int minutes = duration / 60;
        int seconds = duration % 60;
        return String.format("%d:%02d", minutes, seconds);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CallParticipant that)) return false;
        return id != null && id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "CallParticipant{" +
                "id=" + id +
                ", user=" + (user != null ? user.getUsername() : "null") +
                ", status=" + status +
                ", mic=" + microphoneEnabled +
                ", camera=" + cameraEnabled +
                ", screenShare=" + screenSharing +
                '}';
    }
}