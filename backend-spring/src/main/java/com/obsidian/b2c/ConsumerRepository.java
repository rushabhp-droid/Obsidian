package com.obsidian.b2c;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConsumerRepository extends JpaRepository<Consumer, UUID> {

    Optional<Consumer> findByEmail(String email);

    Optional<Consumer> findByGoogleSubject(String googleSubject);
}
