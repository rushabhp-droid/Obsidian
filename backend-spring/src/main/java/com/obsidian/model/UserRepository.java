package com.obsidian.model;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface UserRepository extends JpaRepository<User, UUID> {

    /** Emails are stored lowercase; pass a normalized value. */
    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    @Query(value = "select id from users where gmail_refresh_token_enc is not null", nativeQuery = true)
    List<UUID> findIdsWithGmailConnected();
}
