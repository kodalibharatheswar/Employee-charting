package com.crm.chat.repository;

import com.crm.chat.entity.Call;
import com.crm.chat.entity.CallParticipant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for CallParticipant entity
 * Handles database operations for call participants
 */
@Repository
public interface CallParticipantRepository extends JpaRepository<CallParticipant, Long> {

    /**
     * Find participant by call and user
     */
    @Query("SELECT cp FROM CallParticipant cp WHERE cp.call.id = :callId AND cp.user.id = :userId")
    Optional<CallParticipant> findByCallIdAndUserId(@Param("callId") Long callId, @Param("userId") Long userId);

    /**
     * Find all participants for a call
     */
    @Query("SELECT cp FROM CallParticipant cp WHERE cp.call.id = :callId")
    List<CallParticipant> findByCallId(@Param("callId") Long callId);

    /**
     * Find active participants (currently joined) in a call
     */
    @Query("SELECT cp FROM CallParticipant cp WHERE cp.call.id = :callId AND cp.status = 'JOINED'")
    List<CallParticipant> findActiveParticipantsByCallId(@Param("callId") Long callId);

    /**
     * Count active participants in a call
     */
    @Query("SELECT COUNT(cp) FROM CallParticipant cp WHERE cp.call.id = :callId AND cp.status = 'JOINED'")
    Long countActiveParticipants(@Param("callId") Long callId);

    /**
     * Find all calls a user has participated in
     */
    @Query("SELECT cp FROM CallParticipant cp WHERE cp.user.id = :userId ORDER BY cp.joinedAt DESC")
    List<CallParticipant> findByUserId(@Param("userId") Long userId);
}