package com.crm.chat.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "users")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String username;

    @Column(nullable = false, unique = true, length = 100)
    private String email;

    @Column(nullable = false)
    private String password;

    @Column(nullable = false, length = 100)
    private String fullName;

    @Column(length = 50)
    private String department;

    @Column(length = 50)
    private String designation;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UserStatus status = UserStatus.OFFLINE;

    @Column
    private LocalDateTime lastSeen;

    @Column(nullable = false)
    private Boolean active = true;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "sender", cascade = CascadeType.ALL)
    private Set<Message> sentMessages = new HashSet<>();

    @ManyToMany(mappedBy = "participants")
    private Set<Conversation> conversations = new HashSet<>();

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL)
    private Set<ChatRoomMember> chatRoomMemberships = new HashSet<>();

    public enum UserStatus {
        ONLINE, OFFLINE, AWAY, BUSY
    }

    // Helper methods
    public void setOnline() {
        this.status = UserStatus.ONLINE;
        this.lastSeen = LocalDateTime.now();
    }

    public void setOffline() {
        this.status = UserStatus.OFFLINE;
        this.lastSeen = LocalDateTime.now();
    }

    public boolean isOnline() {
        return this.status == UserStatus.ONLINE;
    }
}
