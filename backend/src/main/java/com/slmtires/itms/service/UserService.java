package com.slmtires.itms.service;

import com.slmtires.itms.dto.ChangePasswordRequest;
import com.slmtires.itms.dto.CreateUserRequest;
import com.slmtires.itms.dto.CreateUserResponse;
import com.slmtires.itms.dto.UpdateUserRequest;
import com.slmtires.itms.dto.UserResponse;
import com.slmtires.itms.entity.Role;
import com.slmtires.itms.entity.User;
import com.slmtires.itms.exception.BadRequestException;
import com.slmtires.itms.exception.ConflictException;
import com.slmtires.itms.exception.ResourceNotFoundException;
import com.slmtires.itms.repository.UserRepository;
import com.slmtires.itms.security.TemporaryPasswordGenerator;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final TemporaryPasswordGenerator passwordGenerator;

    @Transactional(readOnly = true)
    public List<UserResponse> listUsers() {
        return userRepository.findAllByOrderByNameAsc().stream()
            .map(UserResponse::from)
            .toList();
    }

    @Transactional(readOnly = true)
    public UserResponse getUser(Long id) {
        return UserResponse.from(findByIdOrThrow(id));
    }

    @Transactional
    public CreateUserResponse createUser(CreateUserRequest request) {
        if (userRepository.existsByEmailIgnoreCase(request.email())) {
            throw new ConflictException("A user with this email already exists.");
        }

        String temporaryPassword = passwordGenerator.generate();

        User user = new User();
        user.setName(request.name());
        user.setEmail(request.email());
        user.setRole(request.role());
        user.setDepartment(request.department());
        user.setEmployeeCode(nextEmployeeCode(request.role()));
        user.setPasswordHash(passwordEncoder.encode(temporaryPassword));
        user.setActive(true);

        User saved = userRepository.save(user);
        return new CreateUserResponse(UserResponse.from(saved), temporaryPassword);
    }

    @Transactional
    public UserResponse updateUser(Long id, UpdateUserRequest request) {
        User user = findByIdOrThrow(id);
        user.setName(request.name());
        user.setDepartment(request.department());
        return UserResponse.from(userRepository.save(user));
    }

    @Transactional
    public UserResponse setActive(Long id, boolean active) {
        User user = findByIdOrThrow(id);
        user.setActive(active);
        return UserResponse.from(userRepository.save(user));
    }

    /** Self-service, any role - the caller changing their own password. Requires the current
     * password (not just an active session) so a left-open browser can't be used to lock the real
     * owner out. Never logs the caller out of other sessions - JWTs already issued stay valid until
     * they naturally expire, same trade-off as the rest of this app's stateless-JWT design. */
    @Transactional
    public void changeOwnPassword(Long userId, ChangePasswordRequest request) {
        User user = findByIdOrThrow(userId);
        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new BadRequestException("Current password is incorrect.");
        }
        if (passwordEncoder.matches(request.newPassword(), user.getPasswordHash())) {
            throw new BadRequestException("New password must be different from your current password.");
        }
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        userRepository.save(user);
    }

    private User findByIdOrThrow(Long id) {
        return userRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("User not found."));
    }

    private String nextEmployeeCode(Role role) {
        long count = userRepository.countByRole(role);
        String prefix = role == Role.ADMIN ? "ADMIN" : "EMP";
        return "%s-%02d".formatted(prefix, count + 1);
    }
}
