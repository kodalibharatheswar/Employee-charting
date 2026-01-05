package com.crm.chat.service;

import com.crm.chat.entity.User;
import com.crm.chat.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public User registerUser(User user) {
        if (userRepository.existsByUsername(user.getUsername())) {
            throw new RuntimeException("Username already exists");
        }
        if (userRepository.existsByEmail(user.getEmail())) {
            throw new RuntimeException("Email already exists");
        }

        user.setPassword(passwordEncoder.encode(user.getPassword()));
        user.setActive(true);
        user.setStatus(User.UserStatus.OFFLINE);
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());

        return userRepository.save(user);
    }

    public Optional<User> findByUsername(String username) {
        return userRepository.findByUsername(username);
    }

    public Optional<User> findById(Long id) {
        return userRepository.findById(id);
    }

    public List<User> getAllActiveUsers() {
        return userRepository.findByActiveTrue();
    }

    public List<User> getAllActiveUsersExcept(Long currentUserId) {
        return userRepository.findAllActiveUsersExcept(currentUserId);
    }

    public List<User> searchUsers(String searchTerm, Long currentUserId) {
        return userRepository.searchUsers(searchTerm, currentUserId);
    }

    public void updateUserStatus(Long userId, User.UserStatus status) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        user.setStatus(status);
        user.setLastSeen(LocalDateTime.now());
        userRepository.save(user);
    }

    public void setUserOnline(Long userId) {
        updateUserStatus(userId, User.UserStatus.ONLINE);
    }

    public void setUserOffline(Long userId) {
        updateUserStatus(userId, User.UserStatus.OFFLINE);
    }

    public List<User> getOnlineUsers() {
        return userRepository.findByStatus(User.UserStatus.ONLINE);
    }

    public boolean validatePassword(User user, String rawPassword) {
        return passwordEncoder.matches(rawPassword, user.getPassword());
    }

    public User updateUser(User user) {
        user.setUpdatedAt(LocalDateTime.now());
        return userRepository.save(user);
    }
}
