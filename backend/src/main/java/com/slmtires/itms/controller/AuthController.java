package com.slmtires.itms.controller;

import com.slmtires.itms.dto.ChangePasswordRequest;
import com.slmtires.itms.dto.LoginRequest;
import com.slmtires.itms.dto.LoginResponse;
import com.slmtires.itms.dto.UserResponse;
import com.slmtires.itms.security.AppUserPrincipal;
import com.slmtires.itms.service.AuthService;
import com.slmtires.itms.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final UserService userService;

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @GetMapping("/me")
    public UserResponse me(@AuthenticationPrincipal AppUserPrincipal principal) {
        return UserResponse.from(principal.getUser());
    }

    /** Any authenticated user (Admin or team member) changing their own password. */
    @PutMapping("/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changePassword(@Valid @RequestBody ChangePasswordRequest request, @AuthenticationPrincipal AppUserPrincipal principal) {
        userService.changeOwnPassword(principal.getId(), request);
    }
}
