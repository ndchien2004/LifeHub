package com.lifehub.infrastructure.persistence;

import com.lifehub.domain.system.RecurringRule;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Spring Data plumbing for the recurring rule table. */
public interface SpringDataRecurringRuleRepository extends JpaRepository<RecurringRule, String> {

    @Query("SELECT r FROM RecurringRule r ORDER BY r.nextRunDate ASC NULLS LAST")
    List<RecurringRule> findAllOrdered();

    @Query("SELECT r FROM RecurringRule r WHERE r.active = true AND r.nextRunDate IS NOT NULL "
            + "AND r.nextRunDate <= :today ORDER BY r.nextRunDate ASC")
    List<RecurringRule> findDue(@Param("today") LocalDate today);
}
