package com.crm.chat.dto;

import com.crm.chat.entity.Call;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Data Transfer Object for Call entity
 * Used for API responses and WebSocket messages related to audio/video calls
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CallDTO {

    private Long id;
    private CallType callType;
    private CallMode callMode;
    private Long conversationId;
    private Long chatRoomId;
    private Long callerId;
    private String callerName;
    private String callerUsername;
    private CallStatus status;
    private LocalDateTime startedAt;
    private LocalDateTime endedAt;
    private Integer duration; // in seconds
    private LocalDateTime createdAt;
    private List<CallParticipantDTO> participants;
    private String roomId; // Used for WebRTC signaling
    
    // Enums matching the Call entity
    public enum CallType {
        AUDIO,
        VIDEO,
        SCREEN_SHARE
    }

    public enum CallMode {
        DIRECT,    // 1-on-1 call
        GROUP      // Group call (multiple participants)
    }

    public enum CallStatus {
        INITIATED,  // Call just created
        RINGING,    // Ringing on recipient's end
        ONGOING,    // Call in progress
        ENDED,      // Call ended normally
        MISSED,     // Recipient didn't answer
        REJECTED,   // Recipient rejected the call
        CANCELLED,  // Caller cancelled before recipient answered
        FAILED      // Technical failure
    }

    /**
     * Convert Call entity to DTO
     */
    public static CallDTO fromEntity(Call call) {
        if (call == null) return null;

        CallDTO dto = new CallDTO();
        dto.setId(call.getId());
        dto.setCallType(CallType.valueOf(call.getCallType().name()));
        dto.setCallMode(CallMode.valueOf(call.getCallMode().name()));
        
        if (call.getConversation() != null) {
            dto.setConversationId(call.getConversation().getId());
        }
        
        if (call.getChatRoom() != null) {
            dto.setChatRoomId(call.getChatRoom().getId());
        }
        
        dto.setCallerId(call.getCaller().getId());
        dto.setCallerName(call.getCaller().getFullName());
        dto.setCallerUsername(call.getCaller().getUsername());
        dto.setStatus(CallStatus.valueOf(call.getStatus().name()));
        dto.setStartedAt(call.getStartedAt());
        dto.setEndedAt(call.getEndedAt());
        dto.setDuration(call.getDuration());
        dto.setCreatedAt(call.getCreatedAt());
        dto.setRoomId(call.getRoomId());
        
        // Map participants if available
        if (call.getParticipants() != null && !call.getParticipants().isEmpty()) {
            dto.setParticipants(
                call.getParticipants().stream()
                    .map(CallParticipantDTO::fromEntity)
                    .collect(Collectors.toList())
            );
        }
        
        return dto;
    }

    /**
     * Create a simplified DTO for call initiation (WebRTC signaling)
     */
    public static CallDTO forInitiation(Long callerId, String callerName, String callerUsername,
                                        CallType callType, CallMode callMode, String roomId) {
        CallDTO dto = new CallDTO();
        dto.setCallerId(callerId);
        dto.setCallerName(callerName);
        dto.setCallerUsername(callerUsername);
        dto.setCallType(callType);
        dto.setCallMode(callMode);
        dto.setStatus(CallStatus.INITIATED);
        dto.setRoomId(roomId);
        dto.setCreatedAt(LocalDateTime.now());
        return dto;
    }

    /**
     * Create DTO for call notification
     */
    public static CallDTO forNotification(Long id, Long callerId, String callerName, 
                                          CallType callType, CallMode callMode, 
                                          String roomId, CallStatus status) {
        CallDTO dto = new CallDTO();
        dto.setId(id);
        dto.setCallerId(callerId);
        dto.setCallerName(callerName);
        dto.setCallType(callType);
        dto.setCallMode(callMode);
        dto.setRoomId(roomId);
        dto.setStatus(status);
        return dto;
    }

    /**
     * Nested DTO for Call Participants
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CallParticipantDTO {
        private Long id;
        private Long callId;
        private Long userId;
        private String userName;
        private String userUsername;
        private LocalDateTime joinedAt;
        private LocalDateTime leftAt;
        private ParticipantStatus status;
        
        public enum ParticipantStatus {
            INVITED,   // Invited but not yet responded
            JOINED,    // Actively in the call
            LEFT,      // Left the call
            REJECTED   // Rejected the invitation
        }
        
        /**
         * Convert CallParticipant entity to DTO
         */
        public static CallParticipantDTO fromEntity(com.crm.chat.entity.CallParticipant participant) {
            if (participant == null) return null;
            
            CallParticipantDTO dto = new CallParticipantDTO();
            dto.setId(participant.getId());
            dto.setCallId(participant.getCall().getId());
            dto.setUserId(participant.getUser().getId());
            dto.setUserName(participant.getUser().getFullName());
            dto.setUserUsername(participant.getUser().getUsername());
            dto.setJoinedAt(participant.getJoinedAt());
            dto.setLeftAt(participant.getLeftAt());
            dto.setStatus(ParticipantStatus.valueOf(participant.getStatus().name()));
            
            return dto;
        }
    }

    /**
     * Helper method to check if call is active
     */
    public boolean isActive() {
        return status == CallStatus.ONGOING || status == CallStatus.RINGING;
    }

    /**
     * Helper method to check if call is finished
     */
    public boolean isFinished() {
        return status == CallStatus.ENDED || 
               status == CallStatus.MISSED || 
               status == CallStatus.REJECTED || 
               status == CallStatus.CANCELLED ||
               status == CallStatus.FAILED;
    }

    /**
     * Helper method to check if it's a direct call
     */
    public boolean isDirectCall() {
        return callMode == CallMode.DIRECT;
    }

    /**
     * Helper method to check if it's a group call
     */
    public boolean isGroupCall() {
        return callMode == CallMode.GROUP;
    }

    /**
     * Helper method to check if it's a video call
     */
    public boolean isVideoCall() {
        return callType == CallType.VIDEO;
    }

    /**
     * Helper method to check if it's an audio call
     */
    public boolean isAudioCall() {
        return callType == CallType.AUDIO;
    }

    /**
     * Helper method to check if it's screen sharing
     */
    public boolean isScreenShare() {
        return callType == CallType.SCREEN_SHARE;
    }

    /**
     * Calculate call duration if ended
     */
    public String getFormattedDuration() {
        if (duration == null || duration == 0) {
            return "00:00";
        }
        
        int hours = duration / 3600;
        int minutes = (duration % 3600) / 60;
        int seconds = duration % 60;
        
        if (hours > 0) {
            return String.format("%02d:%02d:%02d", hours, minutes, seconds);
        } else {
            return String.format("%02d:%02d", minutes, seconds);
        }
    }
}