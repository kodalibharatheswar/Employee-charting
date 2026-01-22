package com.crm.chat.repository;

import com.crm.chat.entity.Message;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MessageRepository extends JpaRepository<Message, Long> {

    // Direct messages (one-to-one)
    List<Message> findByConversationIdOrderByCreatedAtAsc(Long conversationId);

    Page<Message> findByConversationIdOrderByCreatedAtDesc(Long conversationId, Pageable pageable);

    @Query("SELECT COUNT(m) FROM Message m WHERE m.conversation.id = :conversationId " +
            "AND m.isRead = false AND m.sender.id != :userId")
    Long countUnreadMessagesInConversation(@Param("conversationId") Long conversationId,
                                           @Param("userId") Long userId);

    // Group messages
    List<Message> findByChatRoomIdOrderByCreatedAtAsc(Long chatRoomId);

    Page<Message> findByChatRoomIdOrderByCreatedAtDesc(Long chatRoomId, Pageable pageable);

    @Query("SELECT COUNT(m) FROM Message m WHERE m.chatRoom.id = :chatRoomId " +
            "AND m.createdAt > (SELECT crm.lastReadAt FROM ChatRoomMember crm " +
            "WHERE crm.chatRoom.id = :chatRoomId AND crm.user.id = :userId)")
    Long countUnreadMessagesInChatRoom(@Param("chatRoomId") Long chatRoomId,
                                       @Param("userId") Long userId);

    // General queries
    @Query("SELECT m FROM Message m WHERE m.deleted = false " +
            "AND (m.conversation.id = :conversationId OR m.chatRoom.id = :chatRoomId) " +
            "ORDER BY m.createdAt DESC")
    List<Message> findRecentMessages(@Param("conversationId") Long conversationId,
                                     @Param("chatRoomId") Long chatRoomId,
                                     Pageable pageable);


        /**
     * Search messages in a direct conversation
     */
    @Query("SELECT m FROM Message m WHERE m.conversation.id = :conversationId " +
           "AND LOWER(m.content) LIKE LOWER(CONCAT('%', :query, '%')) " +
           "AND m.deleted = false ORDER BY m.createdAt DESC")
    List<Message> searchConversationMessages(@Param("conversationId") Long conversationId, @Param("query") String query);

    /**
     * Search messages in a chat room
     */
    @Query("SELECT m FROM Message m WHERE m.chatRoom.id = :chatRoomId " +
           "AND LOWER(m.content) LIKE LOWER(CONCAT('%', :query, '%')) " +
           "AND m.deleted = false ORDER BY m.createdAt DESC")
    List<Message> searchChatRoomMessages(@Param("chatRoomId") Long chatRoomId, @Param("query") String query);                               

    void deleteByConversationId(Long conversationId);

    void deleteByChatRoomId(Long chatRoomId);
}
