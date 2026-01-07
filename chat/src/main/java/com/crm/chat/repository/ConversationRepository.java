package com.crm.chat.repository;

import com.crm.chat.entity.Conversation;
import com.crm.chat.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ConversationRepository extends JpaRepository<Conversation, Long> {

    @Query("SELECT c FROM Conversation c JOIN c.participants p WHERE p.id = :userId " +
            "ORDER BY c.lastMessageAt DESC NULLS LAST, c.createdAt DESC")
    List<Conversation> findByParticipantId(@Param("userId") Long userId);


    // NEW QUERY - Returns the first result if multiple exist
    @Query(value = "SELECT DISTINCT c.* FROM conversations c " +
           "JOIN conversation_participants p1 ON c.id = p1.conversation_id " +
           "JOIN conversation_participants p2 ON c.id = p2.conversation_id " +
           "WHERE p1.user_id = :user1Id AND p2.user_id = :user2Id " +
           "AND (SELECT COUNT(*) FROM conversation_participants cp WHERE cp.conversation_id = c.id) = 2 " +
           "ORDER BY c.created_at DESC LIMIT 1", 
           nativeQuery = true)
    Optional<Conversation> findConversationBetweenUsers(@Param("user1Id") Long user1Id, @Param("user2Id") Long user2Id);
   /*  @Query("SELECT c FROM Conversation c JOIN c.participants p1 JOIN c.participants p2 " +
            "WHERE p1.id = :user1Id AND p2.id = :user2Id AND SIZE(c.participants) = 2")
    Optional<Conversation> findConversationBetweenUsers(@Param("user1Id") Long user1Id,
                                                        @Param("user2Id") Long user2Id); */

    @Query("SELECT CASE WHEN COUNT(c) > 0 THEN true ELSE false END FROM Conversation c " +
            "JOIN c.participants p1 JOIN c.participants p2 " +
            "WHERE p1.id = :user1Id AND p2.id = :user2Id AND SIZE(c.participants) = 2")
    boolean existsConversationBetweenUsers(@Param("user1Id") Long user1Id,
                                           @Param("user2Id") Long user2Id);

    @Query("SELECT c FROM Conversation c JOIN c.participants p " +
            "WHERE p = :user ORDER BY c.updatedAt DESC")
    List<Conversation> findRecentConversationsByUser(@Param("user") User user);

    @Query("SELECT COUNT(m) FROM Message m WHERE m.conversation.id = :conversationId " +
            "AND m.isRead = false AND m.sender.id != :userId")
    Long countUnreadMessages(@Param("conversationId") Long conversationId,
                             @Param("userId") Long userId);

// Alternative: Return list and handle in service
    @Query("SELECT c FROM Conversation c JOIN c.participants p1 JOIN c.participants p2 " +
           "WHERE p1.id = :user1Id AND p2.id = :user2Id AND SIZE(c.participants) = 2")
    List<Conversation> findAllConversationsBetweenUsers(@Param("user1Id") Long user1Id, @Param("user2Id") Long user2Id);

}
