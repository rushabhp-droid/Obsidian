package com.obsidian.org;

import com.obsidian.model.Account;
import com.obsidian.model.Role;
import com.obsidian.org.OrganizationService.Member;
import com.obsidian.org.OrganizationService.Settings;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Settings → Organization and Users &amp; roles in the dashboard. Organization admins only. */
@RestController
@RequestMapping("/api/v1/org")
public class OrganizationController {

    private final OrganizationService service;

    public OrganizationController(OrganizationService service) {
        this.service = service;
    }

    public record UpdateRequest(@Size(max = 120) String name, Boolean policyAllowUserBypass) {}

    /** The admin hands the temporary password to the member; they sign in with it (and can add a passkey). */
    public record AddMemberRequest(
            @NotBlank @Email @Size(max = 255) String email,
            @Size(max = 120) String displayName,
            @NotNull Role role,
            @NotBlank @Size(min = 12, max = 128) String temporaryPassword) {}

    public record RoleRequest(@NotNull Role role) {}

    @GetMapping
    public Settings settings(Account account) {
        return service.settings(account);
    }

    @PutMapping
    public Settings update(Account account, @Valid @RequestBody UpdateRequest body) {
        return service.update(account, body.name(), body.policyAllowUserBypass());
    }

    @GetMapping("/members")
    public List<Member> members(Account account) {
        return service.members(account);
    }

    @PostMapping("/members")
    public ResponseEntity<Member> add(Account account, @Valid @RequestBody AddMemberRequest body) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(service.add(account, body.email(), body.displayName(), body.role(), body.temporaryPassword()));
    }

    @PutMapping("/members/{id}/role")
    public Member changeRole(Account account, @PathVariable UUID id, @Valid @RequestBody RoleRequest body) {
        return service.changeRole(account, id, body.role());
    }

    @DeleteMapping("/members/{id}")
    public ResponseEntity<Void> remove(Account account, @PathVariable UUID id) {
        service.remove(account, id);
        return ResponseEntity.noContent().build();
    }

    @ExceptionHandler(OrganizationService.MemberNotFoundException.class)
    ProblemDetail notFound(OrganizationService.MemberNotFoundException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
    }
}
