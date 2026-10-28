package com.obsidian.org;

import com.obsidian.audit.AuditService;
import com.obsidian.auth.AuthExceptions;
import com.obsidian.config.ForbiddenException;
import com.obsidian.model.Account;
import com.obsidian.model.AuditAction;
import com.obsidian.model.Organization;
import com.obsidian.model.OrganizationRepository;
import com.obsidian.model.Role;
import com.obsidian.model.User;
import com.obsidian.model.UserRepository;
import com.obsidian.rules.RuleConflictException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * An organization's settings and members, for its admins. Members are added
 * with a temporary password (there is no email delivery for dashboard
 * accounts); every change is audited. An organization always keeps at least
 * one admin, and an admin can't remove themselves.
 */
@Service
public class OrganizationService {

    private final OrganizationRepository organizations;
    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final AuditService audit;
    private final JdbcTemplate jdbc;

    public OrganizationService(OrganizationRepository organizations, UserRepository users, PasswordEncoder encoder,
            AuditService audit, JdbcTemplate jdbc) {
        this.organizations = organizations;
        this.users = users;
        this.encoder = encoder;
        this.audit = audit;
        this.jdbc = jdbc;
    }

    public record Settings(UUID id, String name, boolean policyAllowUserBypass, long memberCount, long adminCount,
            java.time.Instant createdAt) {}

    public record Member(UUID id, String email, String displayName, Role role, int passkeys, boolean gmailConnected,
            boolean you, java.time.Instant createdAt) {}

    @Transactional(readOnly = true)
    public Settings settings(Account admin) {
        Organization org = organization(admin);
        return new Settings(org.getId(), org.getName(), org.isPolicyAllowUserBypass(),
                users.findByOrgIdOrderByCreatedAtAsc(org.getId()).size(),
                users.countByOrgIdAndRole(org.getId(), Role.ADMIN), org.getCreatedAt());
    }

    @Transactional
    public Settings update(Account admin, String rawName, Boolean allowUserBypass) {
        Organization org = organization(admin);
        Map<String, Object> details = new LinkedHashMap<>();
        if (rawName != null) {
            String name = rawName.trim().replaceAll("\\s+", " ");
            if (name.isEmpty()) {
                throw new AuthExceptions.BadRequest("Enter the organization's name.");
            }
            if (!name.equals(org.getName())) {
                details.put("name", Map.of("previous", org.getName(), "new", name));
                org.setName(name);
            }
        }
        if (allowUserBypass != null && allowUserBypass != org.isPolicyAllowUserBypass()) {
            details.put("policyAllowUserBypass", Map.of("previous", org.isPolicyAllowUserBypass(), "new", allowUserBypass));
            org.setPolicyAllowUserBypass(allowUserBypass);
        }
        if (!details.isEmpty()) {
            organizations.save(org);
            audit.record(AuditAction.ORG_UPDATE, org.getId(), admin.userId(), null, details);
        }
        return settings(admin);
    }

    @Transactional(readOnly = true)
    public List<Member> members(Account admin) {
        Organization org = organization(admin);
        return users.findByOrgIdOrderByCreatedAtAsc(org.getId()).stream().map(u -> member(u, admin)).toList();
    }

    @Transactional
    public Member add(Account admin, String rawEmail, String rawName, Role role, String temporaryPassword) {
        Organization org = organization(admin);
        String email = rawEmail.trim().toLowerCase(Locale.ROOT);
        if (role != Role.EMPLOYEE && role != Role.ADMIN) {
            throw new AuthExceptions.BadRequest("A member is an EMPLOYEE or an ADMIN.");
        }
        if (users.existsByEmail(email)) {
            throw new AuthExceptions.AccountExists();
        }
        User user = new User(email, role);
        user.setOrgId(org.getId());
        String name = rawName == null ? "" : rawName.trim().replaceAll("\\s+", " ");
        user.setDisplayName(name.isEmpty() ? null : name);
        user.setPasswordHash(encoder.encode(temporaryPassword));
        user = users.save(user);
        audit.record(AuditAction.MEMBER_ADD, org.getId(), admin.userId(), null,
                details(user, Map.of("role", role.name())));
        return member(user, admin);
    }

    @Transactional
    public Member changeRole(Account admin, UUID memberId, Role role) {
        Organization org = organization(admin);
        User user = memberOf(org, memberId);
        if (role != Role.EMPLOYEE && role != Role.ADMIN) {
            throw new AuthExceptions.BadRequest("A member is an EMPLOYEE or an ADMIN.");
        }
        Role previous = user.getRole();
        if (previous == role) {
            return member(user, admin);
        }
        if (previous == Role.ADMIN && users.countByOrgIdAndRole(org.getId(), Role.ADMIN) <= 1) {
            throw new RuleConflictException("An organization needs at least one admin. Make someone else an admin first.");
        }
        user.setRole(role);
        users.save(user);
        audit.record(AuditAction.MEMBER_ROLE, org.getId(), admin.userId(), null,
                details(user, Map.of("previous", previous.name(), "new", role.name())));
        return member(user, admin);
    }

    /** Removes the account; its sessions and mobile tokens stop working on their next request. */
    @Transactional
    public void remove(Account admin, UUID memberId) {
        Organization org = organization(admin);
        User user = memberOf(org, memberId);
        if (user.getId().equals(admin.userId())) {
            throw new RuleConflictException("You can't remove your own account. Ask another admin.");
        }
        if (user.getRole() == Role.ADMIN && users.countByOrgIdAndRole(org.getId(), Role.ADMIN) <= 1) {
            throw new RuleConflictException("An organization needs at least one admin.");
        }
        audit.record(AuditAction.MEMBER_REMOVE, org.getId(), admin.userId(), null,
                details(user, Map.of("role", user.getRole().name())));
        // Passkeys live in Spring Security's tables, keyed by the email (user_entities.name).
        jdbc.update("delete from user_credentials where user_entity_user_id in (select id from user_entities where name = ?)",
                user.getEmail());
        jdbc.update("delete from user_entities where name = ?", user.getEmail());
        users.delete(user);
    }

    private Organization organization(Account account) {
        if (!account.isAdmin()) {
            throw new ForbiddenException("Only organization admins can manage the organization.");
        }
        return organizations.findById(account.orgId()).orElseThrow();
    }

    private User memberOf(Organization org, UUID memberId) {
        return users.findById(memberId).filter(u -> org.getId().equals(u.getOrgId()))
                .orElseThrow(() -> new MemberNotFoundException());
    }

    private Member member(User user, Account viewer) {
        Integer passkeys = jdbc.queryForObject("""
                select count(*) from user_credentials c join user_entities e on e.id = c.user_entity_user_id
                where e.name = ?""", Integer.class, user.getEmail());
        return new Member(user.getId(), user.getEmail(), displayName(user), user.getRole(),
                passkeys == null ? 0 : passkeys, user.getGmailAddress() != null, user.getId().equals(viewer.userId()),
                user.getCreatedAt());
    }

    private static String displayName(User user) {
        return user.getDisplayName() != null && !user.getDisplayName().isBlank()
                ? user.getDisplayName()
                : user.getEmail().substring(0, user.getEmail().indexOf('@'));
    }

    private static Map<String, Object> details(User user, Map<String, Object> extra) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("memberId", user.getId().toString());
        details.put("memberEmail", user.getEmail());
        details.putAll(extra);
        return details;
    }

    /** 404 for a member id that isn't in the admin's organization. */
    public static class MemberNotFoundException extends RuntimeException {
        public MemberNotFoundException() {
            super("No such member in your organization.");
        }
    }
}
