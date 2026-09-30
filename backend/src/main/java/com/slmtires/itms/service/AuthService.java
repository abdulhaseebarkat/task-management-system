package com.slmtires.itms.service;

import com.slmtires.itms.dto.LoginRequest;
import com.slmtires.itms.dto.LoginResponse;
import com.slmtires.itms.dto.UserResponse;
import com.slmtires.itms.entity.User;
import com.slmtires.itms.exception.InvalidCredentialsException;
import com.slmtires.itms.repository.UserRepository;
import com.slmtires.itms.security.AppUserPrincipal;
import com.slmtires.itms.security.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public LoginResponse login(LoginRequest request) {
        User user = userRepository.findByEmailIgnoreCase(request.email())
            .orElseThrow(InvalidCredentialsException::new);

        if (!user.isActive() || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }

        String token = jwtService.generateToken(new AppUserPrincipal(user));
        return new LoginResponse(token, UserResponse.from(user));
    }
}
