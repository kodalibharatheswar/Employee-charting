package com.crm.chat.repository;

import com.crm.chat.entity.Call;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface CallRepository extends JpaRepository<Call, Long> {

    // ==================== FIND BY STATUS ====================

    /**
     * Find all active calls (ONGOING or RINGING)
     */
    @Query("SELECT c FROM Call c WHERE c.status IN ('ONGOING', 'RINGING') " +
           "ORDER BY c.createdAt DESC")
    List<Call> findActiveCalls();

    /**
     * Find active call by room ID
     */
    @Query("SELECT c FROM Call c WHERE c.roomId = :roomId AND c.status = 'ONGOING'")
    Optional<Call> findActiveCallByRoomId(@Param("roomId") String roomId);

    /**
     * Find calls by status
     */
    List<Call> findByStatusOrderByCreatedAtDesc(Call.CallStatus status);

    // ==================== FIND BY CONVERSATION (Direct Calls) ====================

    /**
     * Find all calls for a specific conversation
     */
    @Query("SELECT c FROM Call c WHERE c.conversation.id = :conversationId " +
           "ORDER BY c.createdAt DESC")
    List<Call> findByConversationId(@Param("conversationId") Long conversationId);

    /**
     * Find calls by conversation ordered by created date descending
     */
    @Query("SELECT c FROM Call c WHERE c.conversation.id = :conversationId " +
           "ORDER BY c.createdAt DESC")
    List<Call> findByConversationIdOrderByCreatedAtDesc(@Param("conversationId") Long conversationId);

    /**
     * Find active call in a conversation
     */
    @Query("SELECT c FROM Call c WHERE c.conversation.id = :conversationId " +
           "AND c.status IN ('ONGOING', 'RINGING')")
    Optional<Call> findActiveCallByConversationId(@Param("conversationId") Long conversationId);

    /**
     * Find recent calls in a conversation (last 20)
     */
    @Query("SELECT c FROM Call c WHERE c.conversation.id = :conversationId " +
           "ORDER BY c.createdAt DESC LIMIT 20")
    List<Call> findRecentCallsByConversationId(@Param("conversationId") Long conversationId);

    /**
     * Count missed calls in a conversation for a specific user
     */
    @Query("SELECT COUNT(c) FROM Call c " +
           "WHERE c.conversation.id = :conversationId " +
           "AND c.status = 'MISSED' " +
           "AND c.caller.id != :userId")
    Long countMissedCallsByConversationForUser(@Param("conversationId") Long conversationId, 
                                                @Param("userId") Long userId);

    // ==================== FIND BY CHAT ROOM (Group Calls) ====================

    /**
     * Find all calls for a specific chat room
     */
    @Query("SELECT c FROM Call c WHERE c.chatRoom.id = :chatRoomId " +
           "ORDER BY c.createdAt DESC")
    List<Call> findByChatRoomId(@Param("chatRoomId") Long chatRoomId);

    /**
     * Find calls by chat room ordered by created date descending
     */
    @Query("SELECT c FROM Call c WHERE c.chatRoom.id = :chatRoomId " +
           "ORDER BY c.createdAt DESC")
    List<Call> findByChatRoomIdOrderByCreatedAtDesc(@Param("chatRoomId") Long chatRoomId);

    /**
     * Find active call in a chat room
     */
    @Query("SELECT c FROM Call c WHERE c.chatRoom.id = :chatRoomId " +
           "AND c.status IN ('ONGOING', 'RINGING')")
    Optional<Call> findActiveCallByChatRoomId(@Param("chatRoomId") Long chatRoomId);

    /**
     * Find recent calls in a chat room (last 20)
     */
    @Query("SELECT c FROM Call c WHERE c.chatRoom.id = :chatRoomId " +
           "ORDER BY c.createdAt DESC LIMIT 20")
    List<Call> findRecentCallsByChatRoomId(@Param("chatRoomId") Long chatRoomId);

    // ==================== FIND BY USER ====================

    /**
     * Find all calls initiated by a user
     */
    @Query("SELECT c FROM Call c WHERE c.caller.id = :userId " +
           "ORDER BY c.createdAt DESC")
    List<Call> findByCallerId(@Param("userId") Long userId);

    /**
     * Find all calls where user is a participant (caller or participant)
     */
    @Query("SELECT DISTINCT c FROM Call c " +
           "LEFT JOIN c.participants p " +
           "WHERE c.caller.id = :userId OR p.user.id = :userId " +
           "ORDER BY c.createdAt DESC")
    List<Call> findCallsByUserId(@Param("userId") Long userId);

    /**
     * Find call history for a user with limit
     * Using JPQL instead of native query to avoid LIMIT parameter issues
     */
    @Query("SELECT DISTINCT c FROM Call c " +
           "LEFT JOIN c.participants p " +
           "WHERE c.caller.id = :userId OR p.user.id = :userId " +
           "ORDER BY c.createdAt DESC")
    List<Call> findCallHistoryByUserId(@Param("userId") Long userId);

    /**
     * Find recent calls for a user (last 50)
     */
    @Query("SELECT DISTINCT c FROM Call c " +
           "LEFT JOIN c.participants p " +
           "WHERE c.caller.id = :userId OR p.user.id = :userId " +
           "ORDER BY c.createdAt DESC LIMIT 50")
    List<Call> findRecentCallsByUserId(@Param("userId") Long userId);

    /**
     * Find active calls for a user (calls user is currently in)
     */
    @Query("SELECT DISTINCT c FROM Call c " +
           "LEFT JOIN c.participants p " +
           "WHERE (c.caller.id = :userId OR p.user.id = :userId) " +
           "AND c.status = 'ONGOING' " +
           "AND (p.status = 'JOINED' OR c.caller.id = :userId)")
    List<Call> findActiveCallsByUserId(@Param("userId") Long userId);

    /**
     * Find active call by user ID
     */
    @Query("SELECT DISTINCT c FROM Call c " +
           "LEFT JOIN c.participants p " +
           "WHERE (c.caller.id = :userId OR p.user.id = :userId) " +
           "AND c.status = 'ONGOING' " +
           "AND (p.status = 'JOINED' OR c.caller.id = :userId)")
    Optional<Call> findActiveCallByUserId(@Param("userId") Long userId);

    /**
     * Count missed calls for a user
     */
    @Query("SELECT COUNT(DISTINCT c) FROM Call c " +
           "LEFT JOIN c.participants p " +
           "WHERE p.user.id = :userId " +
           "AND p.status = 'INVITED' " +
           "AND c.status = 'MISSED'")
    Long countMissedCallsForUser(@Param("userId") Long userId);

    // ==================== FIND BY TYPE ====================

    /**
     * Find calls by type (AUDIO, VIDEO, SCREEN_SHARE)
     */
    List<Call> findByCallTypeOrderByCreatedAtDesc(Call.CallType callType);

    /**
     * Find calls by mode (DIRECT, GROUP)
     */
    List<Call> findByCallModeOrderByCreatedAtDesc(Call.CallMode callMode);

    // ==================== FIND BY DATE RANGE ====================

    /**
     * Find calls within a date range
     */
    @Query("SELECT c FROM Call c WHERE c.createdAt BETWEEN :startDate AND :endDate " +
           "ORDER BY c.createdAt DESC")
    List<Call> findCallsBetweenDates(@Param("startDate") LocalDateTime startDate,
                                      @Param("endDate") LocalDateTime endDate);

    /**
     * Find calls for a user within a date range
     */
    @Query("SELECT DISTINCT c FROM Call c " +
           "LEFT JOIN c.participants p " +
           "WHERE (c.caller.id = :userId OR p.user.id = :userId) " +
           "AND c.createdAt BETWEEN :startDate AND :endDate " +
           "ORDER BY c.createdAt DESC")
    List<Call> findCallsByUserIdBetweenDates(@Param("userId") Long userId,
                                              @Param("startDate") LocalDateTime startDate,
                                              @Param("endDate") LocalDateTime endDate);

    /**
     * Find calls by status and created before a certain date
     */
    @Query("SELECT c FROM Call c WHERE c.status = :status AND c.createdAt < :cutoffDate")
    List<Call> findByStatusAndCreatedAtBefore(@Param("status") Call.CallStatus status, 
                                                @Param("cutoffDate") LocalDateTime cutoffDate);

    // ==================== STATISTICS ====================

    /**
     * Get total call duration for a user
     */
    @Query("SELECT COALESCE(SUM(c.duration), 0) FROM Call c " +
           "LEFT JOIN c.participants p " +
           "WHERE (c.caller.id = :userId OR p.user.id = :userId) " +
           "AND c.status = 'ENDED'")
    Long getTotalCallDurationByUserId(@Param("userId") Long userId);

    /**
     * Get total calls count for a user
     */
    @Query("SELECT COUNT(DISTINCT c) FROM Call c " +
           "LEFT JOIN c.participants p " +
           "WHERE c.caller.id = :userId OR p.user.id = :userId")
    Long getTotalCallCountByUserId(@Param("userId") Long userId);

    /**
     * Get call count by status for a user
     */
    @Query("SELECT COUNT(DISTINCT c) FROM Call c " +
           "LEFT JOIN c.participants p " +
           "WHERE (c.caller.id = :userId OR p.user.id = :userId) " +
           "AND c.status = :status")
    Long getCallCountByUserIdAndStatus(@Param("userId") Long userId, 
                                        @Param("status") Call.CallStatus status);

    /**
     * Get average call duration for a user
     */
    @Query("SELECT COALESCE(AVG(c.duration), 0) FROM Call c " +
           "LEFT JOIN c.participants p " +
           "WHERE (c.caller.id = :userId OR p.user.id = :userId) " +
           "AND c.status = 'ENDED' " +
           "AND c.duration IS NOT NULL")
    Double getAverageCallDurationByUserId(@Param("userId") Long userId);

    // ==================== FIND BY MULTIPLE CRITERIA ====================

    /**
     * Find calls by user and status
     */
    @Query("SELECT DISTINCT c FROM Call c " +
           "LEFT JOIN c.participants p " +
           "WHERE (c.caller.id = :userId OR p.user.id = :userId) " +
           "AND c.status = :status " +
           "ORDER BY c.createdAt DESC")
    List<Call> findCallsByUserIdAndStatus(@Param("userId") Long userId, 
                                           @Param("status") Call.CallStatus status);

    /**
     * Find calls by user and type
     */
    @Query("SELECT DISTINCT c FROM Call c " +
           "LEFT JOIN c.participants p " +
           "WHERE (c.caller.id = :userId OR p.user.id = :userId) " +
           "AND c.callType = :callType " +
           "ORDER BY c.createdAt DESC")
    List<Call> findCallsByUserIdAndType(@Param("userId") Long userId, 
                                         @Param("callType") Call.CallType callType);

    // ==================== CHECK EXISTENCE ====================

    /**
     * Check if there's an active call in a conversation
     */
    @Query("SELECT CASE WHEN COUNT(c) > 0 THEN true ELSE false END FROM Call c " +
           "WHERE c.conversation.id = :conversationId " +
           "AND c.status IN ('ONGOING', 'RINGING')")
    boolean hasActiveCallInConversation(@Param("conversationId") Long conversationId);

    /**
     * Check if there's an active call in a chat room
     */
    @Query("SELECT CASE WHEN COUNT(c) > 0 THEN true ELSE false END FROM Call c " +
           "WHERE c.chatRoom.id = :chatRoomId " +
           "AND c.status IN ('ONGOING', 'RINGING')")
    boolean hasActiveCallInChatRoom(@Param("chatRoomId") Long chatRoomId);

    /**
     * Check if user is currently in any active call
     */
    @Query("SELECT CASE WHEN COUNT(DISTINCT c) > 0 THEN true ELSE false END FROM Call c " +
           "LEFT JOIN c.participants p " +
           "WHERE (c.caller.id = :userId OR p.user.id = :userId) " +
           "AND c.status = 'ONGOING' " +
           "AND (p.status = 'JOINED' OR c.caller.id = :userId)")
    boolean isUserInActiveCall(@Param("userId") Long userId);

    // ==================== DELETE OPERATIONS ====================

    /**
     * Delete calls older than a specific date
     */
    @Query("DELETE FROM Call c WHERE c.createdAt < :cutoffDate AND c.status NOT IN ('ONGOING', 'RINGING')")
    void deleteCallsOlderThan(@Param("cutoffDate") LocalDateTime cutoffDate);

    /**
     * Delete calls by conversation
     */
    void deleteByConversationId(Long conversationId);

    /**
     * Delete calls by chat room
     */
    void deleteByChatRoomId(Long chatRoomId);
}