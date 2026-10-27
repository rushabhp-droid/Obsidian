package com.obsidian.auth;

import com.obsidian.model.User;
import com.obsidian.model.UserRepository;
import java.util.List;
import java.util.Locale;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/**
 * Loads users by email for Spring's WebAuthn login (passkeys map
 * user_entities.name to users.email) and for bearer tokens. The principal
 * never carries the password hash, so sessions in Redis don't either.
 */
@Service
public class ObsidianUserDetailsService implements UserDetailsService {

    private final UserRepository users;

    public ObsidianUserDetailsService(UserRepository users) {
        this.users = users;
    }

    @Override
    public UserDetails loadUserByUsername(String username) {
        return users.findByEmail(username.trim().toLowerCase(Locale.ROOT))
                .map(ObsidianUserDetailsService::principal)
                .orElseThrow(() -> new UsernameNotFoundException("No such user"));
    }

    static UserDetails principal(User user) {
        return new org.springframework.security.core.userdetails.User(user.getEmail(), "",
                List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name())));
    }
}
