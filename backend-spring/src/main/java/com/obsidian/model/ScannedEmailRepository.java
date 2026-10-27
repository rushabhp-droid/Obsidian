package com.obsidian.model;

import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ScannedEmailRepository extends JpaRepository<ScannedEmail, UUID> {

    boolean existsByUserIdAndGmailMessageId(UUID userId, String gmailMessageId);

    List<ScannedEmail> findByUserIdOrderByReceivedAtDesc(UUID userId, Limit limit);

    List<ScannedEmail> findByUserIdAndVerdictOrderByReceivedAtDesc(UUID userId, Verdict verdict, Limit limit);
}
