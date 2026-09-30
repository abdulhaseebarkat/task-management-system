package com.slmtires.itms.config;

import com.slmtires.itms.dto.CreateUserRequest;
import com.slmtires.itms.dto.CreateUserResponse;
import com.slmtires.itms.entity.Role;
import com.slmtires.itms.repository.UserRepository;
import com.slmtires.itms.service.UserService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * One-time bootstrap: creates the real initial accounts if the users table
 * is empty. Each account gets a random temporary password, printed to the
 * console ONCE - it is never recoverable afterward, since only its bcrypt
 * hash is stored. Whoever runs this the first time is responsible for
 * relaying each password to its owner through a private channel.
 */
@Component
@RequiredArgsConstructor
public class InitialUserSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(InitialUserSeeder.class);

    private final UserRepository userRepository;
    private final UserService userService;

    @Override
    public void run(String... args) {
        if (userRepository.count() > 0) {
            return;
        }

        List<CreateUserRequest> initialUsers = List.of(
            new CreateUserRequest("Farrukh Ali", "farrukh.ali@slmtires.com", Role.ADMIN, "IT"),
            new CreateUserRequest("Najeeb Ahmed", "najeeb.ahmed@slmtires.com", Role.TEAM_MEMBER, "IT"),
            new CreateUserRequest("Abdul Haseeb", "abdul.haseeb@slmtires.com", Role.TEAM_MEMBER, "IT"),
            new CreateUserRequest("Farooq Khan", "farooq.khan@slmtires.com", Role.TEAM_MEMBER, "IT"),
            new CreateUserRequest("Munawer Hussain", "munawer.hussain@slmtires.com", Role.TEAM_MEMBER, "IT"),
            new CreateUserRequest("Ashfaque Ali", "ashfaque.ali@slmtires.com", Role.TEAM_MEMBER, "IT")
        );

        log.warn("==================================================================");
        log.warn("Seeding initial accounts. Temporary passwords below are shown ONLY");
        log.warn("this once - they are stored solely as bcrypt hashes from here on.");
        log.warn("==================================================================");

        for (CreateUserRequest request : initialUsers) {
            CreateUserResponse created = userService.createUser(request);
            log.warn("{}  |  {}  |  {}  |  temporary password: {}",
                created.user().employeeCode(),
                created.user().email(),
                created.user().role(),
                created.temporaryPassword());
        }

        log.warn("==================================================================");
    }
}
