package com.obsidian.b2c;

import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConsumerActivityRepository extends JpaRepository<ConsumerActivity, UUID> {

    List<ConsumerActivity> findByConsumerIdOrderByCreatedAtDesc(UUID consumerId, Limit limit);
}
