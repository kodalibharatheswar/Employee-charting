package com.crm.chat.dto;

import com.crm.chat.entity.User;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserDTO {

    private Long id;
    private String username;
    private String email;
    private String fullName;
    private String department;
    private String designation;
    private String status;
    private LocalDateTime lastSeen;
    private Boolean active;

    // Convert Entity to DTO
    public static UserDTO fromEntity(User user) {
        UserDTO dto = new UserDTO();
        dto.setId(user.getId());
        dto.setUsername(user.getUsername());
        dto.setEmail(user.getEmail());
        dto.setFullName(user.getFullName());
        dto.setDepartment(user.getDepartment());
        dto.setDesignation(user.getDesignation());
        dto.setStatus(user.getStatus().name());
        dto.setLastSeen(user.getLastSeen());
        dto.setActive(user.getActive());
        return dto;
    }
}
