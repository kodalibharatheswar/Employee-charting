package com.crm.chat.repository;

import com.crm.chat.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByUsername(String username);

    Optional<User> findByEmail(String email);

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);

    List<User> findByActiveTrue();

    List<User> findByStatus(User.UserStatus status);

    @Query("SELECT u FROM User u WHERE u.active = true AND u.id != :currentUserId")
    List<User> findAllActiveUsersExcept(@Param("currentUserId") Long currentUserId);

    @Query("SELECT u FROM User u WHERE LOWER(u.fullName) LIKE LOWER(CONCAT('%', :searchTerm, '%')) " +
            "OR LOWER(u.username) LIKE LOWER(CONCAT('%', :searchTerm, '%')) " +
            "AND u.active = true AND u.id != :currentUserId")
    List<User> searchUsers(@Param("searchTerm") String searchTerm, @Param("currentUserId") Long currentUserId);

    @Query("SELECT u FROM User u WHERE u.department = :department AND u.active = true")
    List<User> findByDepartment(@Param("department") String department);
}
