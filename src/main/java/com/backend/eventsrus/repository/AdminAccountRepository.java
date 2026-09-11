package com.backend.eventsrus.repository;

import com.backend.eventsrus.model.AdminAccount;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AdminAccountRepository extends JpaRepository<AdminAccount, Long> {

    Optional<AdminAccount> findByUsernameIgnoreCase(String username);

    boolean existsByUsernameIgnoreCase(String username);

    List<AdminAccount> findAllByOrderByCreatedAtAsc();
}
