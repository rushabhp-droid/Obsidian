package com.obsidian.codeguard;

import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CodeFindingRepository extends JpaRepository<CodeFinding, UUID> {

    List<CodeFinding> findByOrgIdOrderByCreatedAtDesc(UUID orgId, Limit limit);

    boolean existsByOrgIdAndRepoFullNameAndGitRefAndFindingTypeAndRuleIdAndLocation(UUID orgId, String repo, String gitRef,
            CodeFinding.Type type, String ruleId, String location);
}
