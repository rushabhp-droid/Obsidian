package com.obsidian.audit;

import com.obsidian.model.AuditAction;
import com.obsidian.model.AuditLog;
import com.obsidian.model.AuditLogRepository;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Writes audit_logs in the caller's transaction, so a change and its audit row commit together. */
@Service
public class AuditService {

    private final AuditLogRepository repository;

    public AuditService(AuditLogRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public AuditLog record(AuditAction action, UUID orgId, UUID actorUserId, UUID ruleId, Map<String, Object> details) {
        return repository.save(new AuditLog(action, orgId, actorUserId, ruleId, details));
    }
}
