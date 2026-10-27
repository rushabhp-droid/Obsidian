package com.obsidian.model;

import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ScanLogRepository extends JpaRepository<ScanLog, UUID> {

    List<ScanLog> findByUserIdOrderByCreatedAtDesc(UUID userId, Limit limit);

    @Query("select s from ScanLog s where s.userId in (select u.id from User u where u.orgId = :orgId)"
            + " order by s.createdAt desc")
    List<ScanLog> findForOrganization(UUID orgId, Limit limit);
}
