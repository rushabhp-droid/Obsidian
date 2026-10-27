package com.obsidian.model;

import java.util.UUID;

/**
 * The signed-in user as every service sees it, whichever way they signed in
 * (password, passkey or mobile bearer token). Consumers have no organization.
 */
public record Account(
        UUID userId,
        String email,
        String displayName,
        Role role,
        UUID orgId,
        String orgName,
        boolean allowUserBypass) {

    public static Account of(User user, Organization org) {
        return new Account(user.getId(), user.getEmail(), user.getDisplayName(), user.getRole(),
                org == null ? null : org.getId(), org == null ? null : org.getName(),
                org == null || org.isPolicyAllowUserBypass());
    }

    public boolean isAdmin() {
        return role == Role.ADMIN && orgId != null;
    }

    public boolean inOrganization() {
        return orgId != null;
    }
}
