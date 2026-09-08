package com.lifehub.domain.system;

import com.lifehub.domain.common.IdGenerator;
import com.lifehub.domain.common.ValidationException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.Objects;

/**
 * A template that generates transactions on a schedule: rent, a subscription, a salary
 * (FR-FIN-13).
 *
 * <p>Does not extend {@code BaseEntity}. The locked ERD gives this table exactly six columns and no
 * timestamps (03-DATA-MODEL.md 6, item C-3), and inventing {@code created_at} to fit a superclass
 * would change a schema nobody approved.
 *
 * <p>{@code templateJson} holds the transaction to create, serialised. A structured set of columns
 * would duplicate half the transaction table and would have to be migrated in step with it forever;
 * the rule only ever reads the blob back into the same command object it was written from.
 */
@Entity
@Table(name = "recurring_rule")
public class RecurringRule {

    @Id
    @Column(name = "id", nullable = false, updatable = false, length = 36)
    private String id;

    @Column(name = "rrule", nullable = false)
    private String rrule;

    @Column(name = "template_json", nullable = false)
    private String templateJson;

    /** Next date a transaction is due, or null once the series has run out. */
    @Column(name = "next_run_date")
    private LocalDate nextRunDate;

    @Column(name = "last_run_date")
    private LocalDate lastRunDate;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    protected RecurringRule() {
    }

    public RecurringRule(String rrule, String templateJson, LocalDate nextRunDate) {
        this.id = IdGenerator.newId();
        changeRrule(rrule);
        changeTemplate(templateJson);
        this.nextRunDate = nextRunDate;
    }

    public void changeRrule(String rrule) {
        if (rrule == null || rrule.isBlank()) {
            throw new ValidationException("Quy luật lặp không được để trống", "rrule");
        }
        this.rrule = rrule.trim();
    }

    public void changeTemplate(String templateJson) {
        if (templateJson == null || templateJson.isBlank()) {
            throw new ValidationException("Mẫu giao dịch không được để trống", "template");
        }
        this.templateJson = templateJson;
    }

    /** Records that the rule fired for {@code ranOn} and points it at the following occurrence. */
    public void advanceTo(LocalDate ranOn, LocalDate nextRunDate) {
        this.lastRunDate = ranOn;
        this.nextRunDate = nextRunDate;
        if (nextRunDate == null) {
            // The series has no further occurrence - COUNT or UNTIL ran out. Deactivating stops the
            // scheduler from re-examining a rule that can never fire again.
            this.active = false;
        }
    }

    public void activate(boolean active) {
        this.active = active;
    }

    public void reschedule(LocalDate nextRunDate) {
        this.nextRunDate = nextRunDate;
    }

    public boolean isDue(LocalDate today) {
        return active && nextRunDate != null && !nextRunDate.isAfter(today);
    }

    public String getId() {
        return id;
    }

    public String getRrule() {
        return rrule;
    }

    public String getTemplateJson() {
        return templateJson;
    }

    public LocalDate getNextRunDate() {
        return nextRunDate;
    }

    public LocalDate getLastRunDate() {
        return lastRunDate;
    }

    public boolean isActive() {
        return active;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof RecurringRule rule && Objects.equals(id, rule.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
