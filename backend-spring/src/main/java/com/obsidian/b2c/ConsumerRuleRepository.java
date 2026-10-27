package com.obsidian.b2c;

import com.obsidian.model.IndicatorType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConsumerRuleRepository extends JpaRepository<ConsumerRule, UUID> {

    List<ConsumerRule> findByConsumerIdOrderByCreatedAtDesc(UUID consumerId);

    Optional<ConsumerRule> findByConsumerIdAndIndicatorTypeAndIndicatorValue(UUID consumerId, IndicatorType type,
            String value);

    Optional<ConsumerRule> findByIdAndConsumerId(UUID id, UUID consumerId);
}
