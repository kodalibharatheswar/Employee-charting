package com.crm.chat.repository;

import com.crm.chat.entity.ChatRoomMember;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ChatRoomMemberRepository extends JpaRepository<ChatRoomMember, Long> {

    List<ChatRoomMember> findByChatRoomId(Long chatRoomId);

    List<ChatRoomMember> findByUserId(Long userId);

    @Query("SELECT crm FROM ChatRoomMember crm WHERE crm.chatRoom.id = :chatRoomId " +
            "AND crm.user.id = :userId")
    Optional<ChatRoomMember> findByChatRoomIdAndUserId(@Param("chatRoomId") Long chatRoomId,
                                                       @Param("userId") Long userId);

    @Query("SELECT CASE WHEN COUNT(crm) > 0 THEN true ELSE false END FROM ChatRoomMember crm " +
            "WHERE crm.chatRoom.id = :chatRoomId AND crm.user.id = :userId AND crm.active = true")
    boolean isMember(@Param("chatRoomId") Long chatRoomId, @Param("userId") Long userId);

    @Query("SELECT crm FROM ChatRoomMember crm WHERE crm.chatRoom.id = :chatRoomId " +
            "AND crm.role = :role AND crm.active = true")
    List<ChatRoomMember> findByChatRoomIdAndRole(@Param("chatRoomId") Long chatRoomId,
                                                 @Param("role") ChatRoomMember.MemberRole role);

    void deleteByChatRoomIdAndUserId(Long chatRoomId, Long userId);

    @Query("SELECT COUNT(crm) FROM ChatRoomMember crm WHERE crm.chatRoom.id = :chatRoomId " +
            "AND crm.active = true")
    Long countActiveMembersByChatRoomId(@Param("chatRoomId") Long chatRoomId);
}
