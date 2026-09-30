package com.slmtires.itms.repository;

import com.slmtires.itms.entity.Role;
import com.slmtires.itms.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);

    List<User> findAllByOrderByNameAsc();

    List<User> findAllByRoleAndActiveTrueOrderByNameAsc(Role role);

    long countByRole(Role role);
}
