package com.slmtires.itms.dto;

import com.slmtires.itms.entity.Role;
import com.slmtires.itms.entity.User;

public record UserResponse(
    Long id,
    String employeeCode,
    String name,
    String email,
    Role role,
    String department,
    boolean active
) {
    public static UserResponse from(User user) {
        return new UserResponse(
            user.getId(),
            user.getEmployeeCode(),
            user.getName(),
            user.getEmail(),
            user.getRole(),
            user.getDepartment(),
            user.isActive()
        );
    }
}
