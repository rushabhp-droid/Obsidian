package com.obsidian.b2c;

import com.obsidian.b2c.ConsumerReport.Status;
import com.obsidian.model.IndicatorType;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ConsumerReportRepository extends JpaRepository<ConsumerReport, UUID> {

    Optional<ConsumerReport> findByConsumerIdAndIndicatorTypeAndIndicatorValue(UUID consumerId, IndicatorType type,
            String value);

    List<ConsumerReport> findByConsumerIdOrderByCreatedAtDesc(UUID consumerId);

    List<ConsumerReport> findByIndicatorTypeAndIndicatorValue(IndicatorType type, String value);

    List<ConsumerReport> findByStatusInOrderByCreatedAtDesc(Collection<Status> statuses);

    /** Distinct verified consumers who reported this indicator since {@code since}. */
    @Query("""
            select count(distinct r.consumerId) from ConsumerReport r, Consumer c
            where c.id = r.consumerId and c.emailVerified = true
              and r.indicatorType = :type and r.indicatorValue = :value and r.createdAt >= :since""")
    long verifiedReporters(IndicatorType type, String value, Instant since);

    /** Indicators still waiting, oldest first: the recheck job asks VirusTotal about them again. */
    @Query("""
            select distinct r.indicatorType as type, r.indicatorValue as value from ConsumerReport r
            where r.status = com.obsidian.b2c.ConsumerReport.Status.WAITING and r.createdAt >= :since""")
    List<IndicatorKey> waitingSince(Instant since);

    interface IndicatorKey {
        IndicatorType getType();
        String getValue();
    }
}
