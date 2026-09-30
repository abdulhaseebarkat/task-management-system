package com.slmtires.itms.dto;

public record LoginResponse(
    String token,
    UserResponse user
) {
}
