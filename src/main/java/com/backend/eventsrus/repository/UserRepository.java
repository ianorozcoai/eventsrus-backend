package com.backend.eventsrus.repository;

import com.backend.eventsrus.enums.Role;
import com.backend.eventsrus.model.User;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByGoogleId(String googleId);

    Optional<User> findByEmail(String email);

    List<User> findByRole(Role role);

    long countByRole(Role role);
}
