package com.obsidian.auth;

import com.obsidian.model.Account;
import com.obsidian.model.Organization;
import com.obsidian.model.OrganizationRepository;
import com.obsidian.model.User;
import com.obsidian.model.UserRepository;
import java.util.Locale;
import java.util.Optional;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

/** Turns any authenticated principal (password, passkey, bearer) into an {@link Account}. */
@Service
public class AccountService {

    private final UserRepository users;
    private final OrganizationRepository organizations;

    public AccountService(UserRepository users, OrganizationRepository organizations) {
        this.users = users;
        this.organizations = organizations;
    }

    public Optional<Account> find(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            return Optional.empty();
        }
        return users.findByEmail(authentication.getName().toLowerCase(Locale.ROOT)).map(this::of);
    }

    public Account require(Authentication authentication) {
        return find(authentication)
                .orElseThrow(() -> new AuthenticationCredentialsNotFoundException("Sign in first."));
    }

    public Account of(User user) {
        Organization org = user.getOrgId() == null ? null : organizations.findById(user.getOrgId()).orElse(null);
        return Account.of(user, org);
    }

    public SessionInfo sessionInfo(Account account) {
        String displayName = account.displayName() != null && !account.displayName().isBlank()
                ? account.displayName()
                : account.email().substring(0, account.email().indexOf('@'));
        SessionInfo.OrganizationInfo org = account.orgId() == null
                ? null
                : new SessionInfo.OrganizationInfo(account.orgId(), account.orgName(), account.allowUserBypass());
        return new SessionInfo(account.email(), displayName, account.role(), org);
    }
}
