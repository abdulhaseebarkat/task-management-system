package com.slmtires.itms.dto;

/**
 * Returned only once, at creation time, so the Admin can hand the temporary
 * password to the new employee. It is never retrievable again afterward.
 */
public record CreateUserResponse(
    UserResponse user,
    String temporaryPassword
) {
}
