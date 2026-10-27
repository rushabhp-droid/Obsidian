package com.obsidian.auth;

import com.obsidian.model.Role;
import java.util.UUID;

/** GET /api/v1/auth/me; mirrors SessionInfo in web-dashboard/src/lib/types.ts. */
public record SessionInfo(String email, String displayName, Role role, OrganizationInfo organization) {

    public record OrganizationInfo(UUID id, String name, boolean policyAllowUserBypass) {}
}
