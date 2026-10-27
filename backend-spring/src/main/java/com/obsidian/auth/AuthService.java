package com.obsidian.auth;

import com.obsidian.model.Organization;
import com.obsidian.model.OrganizationRepository;
import com.obsidian.model.Role;
import com.obsidian.model.User;
import com.obsidian.model.UserRepository;
import java.util.Locale;
import java.util.Optional;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Password checks and account creation. Passkey login is Spring Security's own filter. */
@Service
public class AuthService {

    private final UserRepository users;
    private final OrganizationRepository organizations;
    private final PasswordEncoder encoder;
    /** Hashed once so unknown emails cost the same Argon2 work as real ones. */
    private final String timingDummy;

    public AuthService(UserRepository users, OrganizationRepository organizations, PasswordEncoder encoder) {
        this.users = users;
        this.organizations = organizations;
        this.encoder = encoder;
        this.timingDummy = encoder.encode("timing dummy for unknown accounts");
    }

    public User authenticate(String email, String password) {
        Optional<User> user = users.findByEmail(normalize(email));
        String hash = user.map(User::getPasswordHash).orElse(null);
        if (hash == null) {
            encoder.matches(password, timingDummy);
            throw new AuthExceptions.InvalidCredentials();
        }
        if (!encoder.matches(password, hash)) {
            throw new AuthExceptions.InvalidCredentials();
        }
        return user.get();
    }

    @Transactional
    public User register(RegisterRequest request) {
        String email = normalize(request.email());
        if (request.client() == ClientType.MOBILE && request.password() == null) {
            throw new AuthExceptions.BadRequest("The mobile app needs a password: Android has no passkeys.");
        }
        if (users.existsByEmail(email)) {
            throw new AuthExceptions.AccountExists();
        }
        Organization org = null;
        if (request.organizationName() != null && !request.organizationName().isBlank()) {
            org = organizations.save(new Organization(request.organizationName().trim()));
        }
        User user = new User(email, org == null ? Role.CONSUMER : Role.ADMIN);
        user.setOrgId(org == null ? null : org.getId());
        if (request.displayName() != null && !request.displayName().isBlank()) {
            user.setDisplayName(request.displayName().trim());
        }
        if (request.password() != null) {
            user.setPasswordHash(encoder.encode(request.password()));
        }
        return users.save(user);
    }

    static String normalize(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }
}
