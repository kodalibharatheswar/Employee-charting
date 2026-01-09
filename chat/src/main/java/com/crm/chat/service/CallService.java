package com.crm.chat.service;

import com.crm.chat.entity.Call;
import com.crm.chat.entity.CallParticipant;
import com.crm.chat.entity.ChatRoom;
import com.crm.chat.entity.Conversation;
import com.crm.chat.entity.User;
import com.crm.chat.repository.CallParticipantRepository;
import com.crm.chat.repository.CallRepository;
import com.crm.chat.repository.ChatRoomRepository;
import com.crm.chat.repository.ConversationRepository;
import com.crm.chat.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class CallService {

    private final CallRepository callRepository;
    private final CallParticipantRepository callParticipantRepository;
    private final UserRepository userRepository;
    private final ConversationRepository conversationRepository;
    private final ChatRoomRepository chatRoomRepository;

    // ==================== DIRECT CALL METHODS ====================

    /**
     * Initiate a direct call (1-on-1) between two users
     */
    public Call initiateDirectCall(Long callerId, Long conversationId, Call.CallType callType) {
        // Validate caller
        User caller = userRepository.findById(callerId)
                .orElseThrow(() -> new RuntimeException("Caller not found"));

        // Validate conversation
        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new RuntimeException("Conversation not found"));

        // Check if there's already an active call in this conversation
        if (callRepository.hasActiveCallInConversation(conversationId)) {
            throw new RuntimeException("There is already an active call in this conversation");
        }

        // Create new call
        Call call = new Call();
        call.setCallType(callType);
        call.setCallMode(Call.CallMode.DIRECT);
        call.setConversation(conversation);
        call.setCaller(caller);
        call.setStatus(Call.CallStatus.RINGING);
        call.setRoomId(generateRoomId());
        call.setCreatedAt(LocalDateTime.now());

        return callRepository.save(call);
    }

    // ==================== GROUP CALL METHODS ====================

    /**
     * Initiate a group call in a chat room
     */
    public Call initiateGroupCall(Long callerId, Long chatRoomId, Call.CallType callType) {
        // Validate caller
        User caller = userRepository.findById(callerId)
                .orElseThrow(() -> new RuntimeException("Caller not found"));

        // Validate chat room
        ChatRoom chatRoom = chatRoomRepository.findById(chatRoomId)
                .orElseThrow(() -> new RuntimeException("Chat room not found"));

        // Check if there's already an active call in this chat room
        if (callRepository.hasActiveCallInChatRoom(chatRoomId)) {
            throw new RuntimeException("There is already an active call in this chat room");
        }

        // Create new call
        Call call = new Call();
        call.setCallType(callType);
        call.setCallMode(Call.CallMode.GROUP);
        call.setChatRoom(chatRoom);
        call.setCaller(caller);
        call.setStatus(Call.CallStatus.RINGING);
        call.setRoomId(generateRoomId());
        call.setCreatedAt(LocalDateTime.now());

        Call savedCall = callRepository.save(call);

        // Auto-add caller as first participant
        addParticipantToCall(savedCall.getId(), callerId);

        return savedCall;
    }

    // ==================== CALL STATE MANAGEMENT ====================

    /**
     * Change call status
     */
    public Call changeCallStatus(Long callId, Call.CallStatus newStatus) {
        Call call = callRepository.findById(callId)
                .orElseThrow(() -> new RuntimeException("Call not found"));

        call.setStatus(newStatus);

        if (newStatus == Call.CallStatus.ONGOING && call.getStartedAt() == null) {
            call.setStartedAt(LocalDateTime.now());
        }

        if (newStatus == Call.CallStatus.ENDED && call.getEndedAt() == null) {
            call.setEndedAt(LocalDateTime.now());
            calculateCallDuration(call);
        }

        return callRepository.save(call);
    }

    /**
     * Add a participant to a call
     */
    public CallParticipant addParticipantToCall(Long callId, Long userId) {
        Call call = callRepository.findById(callId)
                .orElseThrow(() -> new RuntimeException("Call not found"));

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        // Check if participant already exists
        Optional<CallParticipant> existing = callParticipantRepository.findByCallIdAndUserId(callId, userId);
        if (existing.isPresent()) {
            CallParticipant participant = existing.get();
            participant.setStatus(CallParticipant.ParticipantStatus.JOINED);
            participant.setJoinedAt(LocalDateTime.now());
            return callParticipantRepository.save(participant);
        }

        // Create new participant
        CallParticipant participant = new CallParticipant();
        participant.setCall(call);
        participant.setUser(user);
        participant.setStatus(CallParticipant.ParticipantStatus.JOINED);
        participant.setJoinedAt(LocalDateTime.now());
        participant.setMicrophoneEnabled(true);
        participant.setCameraEnabled(call.getCallType() == Call.CallType.VIDEO);
        participant.setScreenSharing(false);

        return callParticipantRepository.save(participant);
    }

    /**
     * Remove a participant from a call
     */
    public void removeParticipantFromCall(Long callId, Long userId) {
        CallParticipant participant = callParticipantRepository.findByCallIdAndUserId(callId, userId)
                .orElseThrow(() -> new RuntimeException("Participant not found in call"));

        participant.setStatus(CallParticipant.ParticipantStatus.LEFT);
        participant.setLeftAt(LocalDateTime.now());
        callParticipantRepository.save(participant);

        // Check if this was the last active participant
        Long activeCount = callParticipantRepository.countActiveParticipants(callId);
        if (activeCount == 0) {
            // Auto-end the call if no one is left
            endCall(callId, userId);
        }
    }

    /**
     * Reject a call
     */
    public Call rejectCall(Long callId, Long userId) {
        Call call = callRepository.findById(callId)
                .orElseThrow(() -> new RuntimeException("Call not found"));

        // Create participant record with REJECTED status
        CallParticipant participant = new CallParticipant();
        participant.setCall(call);
        participant.setUser(userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found")));
        participant.setStatus(CallParticipant.ParticipantStatus.REJECTED);
        callParticipantRepository.save(participant);

        // For direct calls, mark as REJECTED if the recipient rejects
        if (call.isDirectCall()) {
            call.setStatus(Call.CallStatus.REJECTED);
            call.setEndedAt(LocalDateTime.now());
            callRepository.save(call);
        }

        return call;
    }

    /**
     * End a call
     */
    public Call endCall(Long callId, Long userId) {
        Call call = callRepository.findById(callId)
                .orElseThrow(() -> new RuntimeException("Call not found"));

        // Mark all active participants as LEFT
        List<CallParticipant> activeParticipants = callParticipantRepository
                .findActiveParticipantsByCallId(callId);

        for (CallParticipant participant : activeParticipants) {
            participant.setStatus(CallParticipant.ParticipantStatus.LEFT);
            participant.setLeftAt(LocalDateTime.now());
            callParticipantRepository.save(participant);
        }

        // Update call status
        call.setStatus(Call.CallStatus.ENDED);
        call.setEndedAt(LocalDateTime.now());
        calculateCallDuration(call);

        return callRepository.save(call);
    }

    /**
     * Cancel a call (before it's answered)
     */
    public Call cancelCall(Long callId, Long userId) {
        Call call = callRepository.findById(callId)
                .orElseThrow(() -> new RuntimeException("Call not found"));

        // Only caller can cancel
        if (!call.getCaller().getId().equals(userId)) {
            throw new RuntimeException("Only the caller can cancel the call");
        }

        // Only allow cancellation if call hasn't started
        if (call.getStatus() != Call.CallStatus.INITIATED && call.getStatus() != Call.CallStatus.RINGING) {
            throw new RuntimeException("Cannot cancel an ongoing call");
        }

        // FIXED: Changed from CANCELLED to REJECTED (CANCELLED doesn't exist in enum)
        call.setStatus(Call.CallStatus.REJECTED);
        call.setEndedAt(LocalDateTime.now());
        callRepository.save(call);

        // Notify all invited participants
        notifyCallStatusChange(call);

        return call;
    }

    /**
     * Get call history for a user
     */
    public List<Call> getUserCallHistory(Long userId, int limit) {
        List<Call> allCalls = callRepository.findCallHistoryByUserId(userId);
        // Limit results in Java since we can't use LIMIT in native query with parameters
        return allCalls.stream()
                .limit(limit)
                .collect(java.util.stream.Collectors.toList());
    }

    /**
     * Get call history for a conversation
     */
    public List<Call> getConversationCallHistory(Long conversationId) {
        return callRepository.findByConversationIdOrderByCreatedAtDesc(conversationId);
    }

    /**
     * Get call history for a chat room
     */
    public List<Call> getChatRoomCallHistory(Long chatRoomId) {
        return callRepository.findByChatRoomIdOrderByCreatedAtDesc(chatRoomId);
    }

    /**
     * Get active call for a user (if any)
     */
    public Optional<Call> getActiveCallForUser(Long userId) {
        return callRepository.findActiveCallByUserId(userId);
    }

    // ==================== MEDIA CONTROL METHODS ====================
    // These methods are called from WebSocketController but media state is managed on frontend
    // Backend just logs the action for potential future features (like recording indicators)

    /**
     * Toggle microphone state
     */
    public void toggleMicrophone(Long callId, Long userId) {
        CallParticipant participant = callParticipantRepository.findByCallIdAndUserId(callId, userId)
                .orElseThrow(() -> new RuntimeException("Participant not found"));

        participant.setMicrophoneEnabled(!participant.getMicrophoneEnabled());
        callParticipantRepository.save(participant);
    }

    /**
     * Toggle camera state
     */
    public void toggleCamera(Long callId, Long userId) {
        CallParticipant participant = callParticipantRepository.findByCallIdAndUserId(callId, userId)
                .orElseThrow(() -> new RuntimeException("Participant not found"));

        participant.setCameraEnabled(!participant.getCameraEnabled());
        callParticipantRepository.save(participant);
    }

    /**
     * Toggle screen sharing state
     */
    public void toggleScreenShare(Long callId, Long userId) {
        CallParticipant participant = callParticipantRepository.findByCallIdAndUserId(callId, userId)
                .orElseThrow(() -> new RuntimeException("Participant not found"));

        participant.setScreenSharing(!participant.getScreenSharing());
        callParticipantRepository.save(participant);
    }

    // ==================== PARTICIPANT MANAGEMENT ====================

    /**
     * Get all participants in a call
     */
    public List<CallParticipant> getCallParticipants(Long callId) {
        return callParticipantRepository.findByCallId(callId);
    }

    /**
     * Get active participants in a call
     */
    public List<CallParticipant> getActiveParticipants(Long callId) {
        return callParticipantRepository.findActiveParticipantsByCallId(callId);
    }

    /**
     * Count active participants
     */
    public Long countActiveParticipants(Long callId) {
        return callParticipantRepository.countActiveParticipants(callId);
    }

    // ==================== CALL RETRIEVAL ====================

    /**
     * Find call by ID
     */
    public Optional<Call> findById(Long callId) {
        return callRepository.findById(callId);
    }

    /**
     * Get calls for a conversation
     */
    public List<Call> getConversationCalls(Long conversationId) {
        return callRepository.findByConversationId(conversationId);
    }

    /**
     * Get calls for a chat room
     */
    public List<Call> getChatRoomCalls(Long chatRoomId) {
        return callRepository.findByChatRoomId(chatRoomId);
    }

    /**
     * Get all active calls
     */
    public List<Call> getActiveCalls() {
        return callRepository.findActiveCalls();
    }

    // ==================== CALL STATISTICS ====================

    /**
     * Get total call duration for a user (in seconds)
     */
    public Long getTotalCallDuration(Long userId) {
        return callRepository.getTotalCallDurationByUserId(userId);
    }

    /**
     * Get total call count for a user
     */
    public Long getTotalCallCount(Long userId) {
        return callRepository.getTotalCallCountByUserId(userId);
    }

    /**
     * Get missed calls count for a user
     */
    public Long getMissedCallsCount(Long userId) {
        return callRepository.countMissedCallsForUser(userId);
    }

    // ==================== CALL CLEANUP ====================

    /**
     * Clean up old calls (older than specified days)
     */
    public void cleanupOldCalls(int daysToKeep) {
        LocalDateTime cutoffDate = LocalDateTime.now().minusDays(daysToKeep);
        List<Call> oldCalls = callRepository.findByStatusAndCreatedAtBefore(
                Call.CallStatus.ENDED, cutoffDate);

        for (Call call : oldCalls) {
            // Delete participants first (due to foreign key)
            List<CallParticipant> participants = callParticipantRepository.findByCallId(call.getId());
            callParticipantRepository.deleteAll(participants);

            // Then delete call
            callRepository.delete(call);
        }
    }

    /**
     * Forcefully end stale calls (ongoing for more than max duration)
     */
    public void endStaleCalls(int maxDurationHours) {
        LocalDateTime cutoffTime = LocalDateTime.now().minusHours(maxDurationHours);

        List<Call> activeCalls = callRepository.findActiveCalls();
        for (Call call : activeCalls) {
            if (call.getCreatedAt().isBefore(cutoffTime)) {
                call.setStatus(Call.CallStatus.FAILED);
                call.setEndedAt(LocalDateTime.now());
                calculateCallDuration(call);
                callRepository.save(call);
            }
        }
    }

    /**
     * Get recent call history for a user
     */
    public List<Call> getRecentCallHistory(Long userId, int count) {
        List<Call> allCalls = callRepository.findCallHistoryByUserId(userId);
        return allCalls.stream()
                .limit(count)
                .collect(java.util.stream.Collectors.toList());
    }

    // ==================== HELPER METHODS ====================

    /**
     * Generate unique room ID for WebRTC
     */
    private String generateRoomId() {
        return "call-" + UUID.randomUUID().toString();
    }

    /**
     * Calculate call duration
     */
    private void calculateCallDuration(Call call) {
        if (call.getStartedAt() != null && call.getEndedAt() != null) {
            Duration duration = Duration.between(call.getStartedAt(), call.getEndedAt());
            call.setDuration((int) duration.getSeconds());
        }
    }

    /**
     * Notify participants about call status change
     * This is a placeholder - actual notification would be sent via WebSocket
     */
    private void notifyCallStatusChange(Call call) {
        // Notification is handled by WebSocketController
        System.out.println("Call " + call.getId() + " status changed to: " + call.getStatus());
    }

    // ==================== ADDITIONAL METHODS FOR CALL CONTROLLER ====================

    /**
     * Check if user is in any active call
     */
    public boolean isUserInActiveCall(Long userId) {
        return callRepository.isUserInActiveCall(userId);
    }

    /**
     * Accept a call
     */
    public Call acceptCall(Long callId, Long userId) {
        Call call = callRepository.findById(callId)
                .orElseThrow(() -> new RuntimeException("Call not found"));

        // Add user as participant
        addParticipantToCall(callId, userId);

        // If it's a direct call and this is the first accept, start the call
        if (call.isDirectCall() && call.getStatus() == Call.CallStatus.RINGING) {
            call.setStatus(Call.CallStatus.ONGOING);
            call.setStartedAt(LocalDateTime.now());
            callRepository.save(call);
        }

        return call;
    }

    /**
     * Get active call for a conversation
     */
    public Optional<Call> getActiveCallForConversation(Long conversationId) {
        return callRepository.findActiveCallByConversationId(conversationId);
    }

    /**
     * Get active call for a chat room
     */
    public Optional<Call> getActiveCallForChatRoom(Long chatRoomId) {
        return callRepository.findActiveCallByChatRoomId(chatRoomId);
    }

    /**
     * Get call statistics for a user
     */
    public Map<String, Object> getCallStatistics(Long userId) {
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalCalls", getTotalCallCount(userId));
        stats.put("totalDuration", getTotalCallDuration(userId));
        stats.put("missedCalls", getMissedCallsCount(userId));
        stats.put("averageDuration", callRepository.getAverageCallDurationByUserId(userId));
        return stats;
    }

    /**
     * Mark missed calls (cleanup job)
     */
    public void markMissedCalls() {
        List<Call> ringingCalls = callRepository.findByStatusOrderByCreatedAtDesc(Call.CallStatus.RINGING);
        LocalDateTime cutoffTime = LocalDateTime.now().minusMinutes(2);

        for (Call call : ringingCalls) {
            if (call.getCreatedAt().isBefore(cutoffTime)) {
                call.setStatus(Call.CallStatus.MISSED);
                call.setEndedAt(LocalDateTime.now());
                callRepository.save(call);
            }
        }
    }
}