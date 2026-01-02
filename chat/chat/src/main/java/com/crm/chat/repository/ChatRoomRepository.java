package com.crm.chat.repository;

import com.crm.chat.entity.ChatRoom;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ChatRoomRepository extends JpaRepository<ChatRoom, Long> {

    @Query("SELECT DISTINCT cr FROM ChatRoom cr JOIN cr.members m " +
            "WHERE m.user.id = :userId AND cr.active = true " +
            "ORDER BY cr.lastMessageAt DESC NULLS LAST, cr.createdAt DESC")
    List<ChatRoom> findByUserId(@Param("userId") Long userId);

    @Query("SELECT cr FROM ChatRoom cr WHERE cr.createdBy.id = :userId AND cr.active = true " +
            "ORDER BY cr.createdAt DESC")
    List<ChatRoom> findByCreatedById(@Param("userId") Long userId);

    @Query("SELECT cr FROM ChatRoom cr WHERE cr.active = true " +
            "AND LOWER(cr.name) LIKE LOWER(CONCAT('%', :searchTerm, '%')) " +
            "ORDER BY cr.createdAt DESC")
    List<ChatRoom> searchByName(@Param("searchTerm") String searchTerm);

    @Query("SELECT cr FROM ChatRoom cr WHERE cr.type = :type AND cr.active = true " +
            "ORDER BY cr.createdAt DESC")
    List<ChatRoom> findByType(@Param("type") ChatRoom.ChatRoomType type);

    List<ChatRoom> findByActiveTrue();

    @Query("SELECT COUNT(m) FROM Message m WHERE m.chatRoom.id = :chatRoomId " +
            "AND m.createdAt > (SELECT crm.lastReadAt FROM ChatRoomMember crm " +
            "WHERE crm.chatRoom.id = :chatRoomId AND crm.user.id = :userId)")
    Long countUnreadMessages(@Param("chatRoomId") Long chatRoomId,
                             @Param("userId") Long userId);
}
