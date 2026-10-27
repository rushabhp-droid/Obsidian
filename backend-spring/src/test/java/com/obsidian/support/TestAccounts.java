package com.obsidian.support;

import com.obsidian.model.Account;
import com.obsidian.model.Organization;
import com.obsidian.model.OrganizationRepository;
import com.obsidian.model.Role;
import com.obsidian.model.User;
import com.obsidian.model.UserRepository;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/** Creates isolated organizations and users so tests never share rows. */
@Component
public class TestAccounts {

    public static final String PASSWORD = "correct horse battery staple";

    private final OrganizationRepository organizations;
    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;

    public TestAccounts(OrganizationRepository organizations, UserRepository users, PasswordEncoder passwordEncoder) {
        this.organizations = organizations;
        this.users = users;
        this.passwordEncoder = passwordEncoder;
    }

    public record Team(Organization org, Account admin, Account employee, Account colleague) {}

    public Team team(boolean allowUserBypass) {
        String tag = UUID.randomUUID().toString().substring(0, 8);
        Organization org = new Organization("Org " + tag);
        org.setPolicyAllowUserBypass(allowUserBypass);
        organizations.save(org);
        return new Team(org,
                member(org, "admin-" + tag + "@aster.example", Role.ADMIN),
                member(org, "employee-" + tag + "@aster.example", Role.EMPLOYEE),
                member(org, "colleague-" + tag + "@aster.example", Role.EMPLOYEE));
    }

    public Account consumer() {
        return Account.of(user("consumer-" + UUID.randomUUID().toString().substring(0, 8) + "@mail.example",
                Role.CONSUMER, null), null);
    }

    public User user(String email, Role role, Organization org) {
        User user = new User(email, role);
        user.setOrgId(org == null ? null : org.getId());
        user.setPasswordHash(passwordEncoder.encode(PASSWORD));
        return users.save(user);
    }

    private Account member(Organization org, String email, Role role) {
        return Account.of(user(email, role, org), org);
    }
}
