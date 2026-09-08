package com.lifehub.application.finance;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lifehub.application.calendar.RecurrenceExpander;
import com.lifehub.application.finance.FinanceCommands.CreateRecurringRule;
import com.lifehub.application.finance.FinanceCommands.CreateTransaction;
import com.lifehub.application.finance.FinanceCommands.UpdateRecurringRule;
import com.lifehub.domain.common.NotFoundException;
import com.lifehub.domain.common.ValidationException;
import com.lifehub.domain.finance.MoneyTransaction;
import com.lifehub.domain.finance.TransactionSource;
import com.lifehub.domain.system.RecurringRule;
import com.lifehub.domain.system.RecurringRuleRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Recurring transactions: rent, subscriptions, a salary that lands on the same day each month
 * (FR-FIN-13).
 *
 * <p>A rule stores the transaction to create as JSON and the date it is next due. Running the rule
 * catches up on every occurrence that has fallen due since the last run, not just the newest one -
 * an app that was closed for a fortnight must not quietly skip a rent payment.
 *
 * <p>Generated rows carry {@code source = RECURRING} and the rule id, so the user can always see
 * where a transaction they did not type came from, and delete it like any other.
 */
@Service
@Transactional
public class RecurringTransactionService {

    private static final Logger log = LoggerFactory.getLogger(RecurringTransactionService.class);

    /** Safety valve on catch-up, in case a rule and a stale clock disagree badly. */
    private static final int MAX_CATCH_UP = 200;

    private final RecurringRuleRepository ruleRepository;
    private final TransactionWriter writer;
    private final RecurrenceExpander expander;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final ZoneId displayZone;

    public RecurringTransactionService(
            RecurringRuleRepository ruleRepository,
            TransactionWriter writer,
            RecurrenceExpander expander,
            ObjectMapper objectMapper,
            Clock clock,
            ZoneId displayZone) {
        this.ruleRepository = ruleRepository;
        this.writer = writer;
        this.expander = expander;
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.displayZone = displayZone;
    }

    @Transactional(readOnly = true)
    public List<RecurringRule> findAll() {
        return ruleRepository.findAll();
    }

    @Transactional(readOnly = true)
    public RecurringRule findById(String id) {
        return ruleRepository
                .findById(id)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy quy luật định kỳ"));
    }

    /** Reads the transaction template back out of a rule, for the edit form. */
    public CreateTransaction templateOf(RecurringRule rule) {
        try {
            return objectMapper.readValue(rule.getTemplateJson(), CreateTransaction.class);
        } catch (JsonProcessingException e) {
            throw new ValidationException("Mẫu giao dịch của quy luật định kỳ không đọc được");
        }
    }

    public RecurringRule create(CreateRecurringRule command) {
        expander.validate(command.rrule());
        if (command.template() == null) {
            throw new ValidationException("Quy luật định kỳ cần một mẫu giao dịch", "template");
        }

        LocalDate startDate =
                command.startDate() == null ? LocalDate.now(clock.withZone(displayZone)) : command.startDate();

        RecurringRule rule = new RecurringRule(
                command.rrule(),
                serialize(command.template()),
                firstRunOnOrAfter(command.rrule(), seriesStartOf(command.template(), startDate), startDate));
        return ruleRepository.save(rule);
    }

    public RecurringRule update(String id, UpdateRecurringRule command) {
        RecurringRule rule = findById(id);
        LocalDate today = LocalDate.now(clock.withZone(displayZone));

        command.rrule().ifPresent(rrule -> {
            expander.validate(rrule);
            rule.changeRrule(rrule);
            rule.reschedule(firstRunOnOrAfter(rrule, seriesStartOf(templateOf(rule), today), today));
        });
        command.nextRunDate().ifPresent(rule::reschedule);
        command.isActive().ifPresent(active -> rule.activate(Boolean.TRUE.equals(active)));

        return ruleRepository.save(rule);
    }

    public void delete(String id) {
        ruleRepository.delete(findById(id));
    }

    /**
     * Generates every transaction that has come due, for every active rule.
     *
     * <p>One rule failing must not stop the others: a single malformed RRULE would otherwise block
     * the user's rent from ever being recorded.
     *
     * @return the transactions created
     */
    public List<MoneyTransaction> runDue() {
        LocalDate today = LocalDate.now(clock.withZone(displayZone));
        List<MoneyTransaction> created = new ArrayList<>();

        for (RecurringRule rule : ruleRepository.findDue(today)) {
            try {
                created.addAll(run(rule, today));
            } catch (RuntimeException e) {
                log.warn("Bỏ qua quy luật định kỳ {} vì lỗi khi sinh giao dịch", rule.getId(), e);
            }
        }
        return created;
    }

    /** Runs one rule up to and including {@code today}, catching up on missed occurrences. */
    public List<MoneyTransaction> run(RecurringRule rule, LocalDate today) {
        CreateTransaction template = templateOf(rule);
        Instant seriesStart = seriesStartOf(template, rule.getNextRunDate());
        List<MoneyTransaction> created = new ArrayList<>();

        int guard = 0;
        while (rule.isDue(today) && guard++ < MAX_CATCH_UP) {
            LocalDate runDate = rule.getNextRunDate();
            created.add(writer.write(instantiate(template, rule.getId(), runDate)));
            rule.advanceTo(runDate, nextAfter(rule.getRrule(), seriesStart, runDate));
        }

        ruleRepository.save(rule);
        return created;
    }

    /**
     * Builds the concrete transaction for one occurrence date.
     *
     * <p>The time of day comes from the template so a rent payment logged at 09:00 keeps landing at
     * 09:00, while the date comes from the rule.
     */
    private CreateTransaction instantiate(CreateTransaction template, String ruleId, LocalDate runDate) {
        LocalTime timeOfDay = template.occurredAt() == null
                ? LocalTime.NOON
                : template.occurredAt().atZone(displayZone).toLocalTime();

        return new CreateTransaction(
                template.type(),
                template.amount(),
                template.walletId(),
                template.toWalletId(),
                template.categoryId(),
                template.note(),
                runDate.atTime(timeOfDay).atZone(displayZone).toInstant(),
                template.tagIds(),
                TransactionSource.RECURRING,
                null,
                ruleId,
                null);
    }

    /**
     * The moment the series is anchored at.
     *
     * <p>Taken from the template rather than from {@code nextRunDate}, because the seed is what
     * gives a rule such as {@code FREQ=MONTHLY} its day of the month and what a {@code COUNT} is
     * counted from. Re-seeding at each run would restart the count and turn "repeat 6 times" into
     * a series with no end.
     */
    private Instant seriesStartOf(CreateTransaction template, LocalDate fallback) {
        if (template != null && template.occurredAt() != null) {
            return template.occurredAt();
        }
        LocalDate date = fallback == null ? LocalDate.now(clock.withZone(displayZone)) : fallback;
        return date.atStartOfDay(displayZone).toInstant();
    }

    /** First occurrence on or after {@code date}, or null when the rule is already exhausted. */
    private LocalDate firstRunOnOrAfter(String rrule, Instant seriesStart, LocalDate date) {
        Instant boundary = date.atStartOfDay(displayZone).toInstant();
        Optional<Instant> next = expander.nextAfter(
                rrule, seriesStart, displayZone, boundary.minusMillis(1));
        return next.map(instant -> LocalDate.ofInstant(instant, displayZone)).orElse(null);
    }

    private LocalDate nextAfter(String rrule, Instant seriesStart, LocalDate ranOn) {
        Instant endOfRunDay =
                ranOn.plusDays(1).atStartOfDay(displayZone).toInstant().minusMillis(1);
        Optional<Instant> next = expander.nextAfter(rrule, seriesStart, displayZone, endOfRunDay);
        return next.map(instant -> LocalDate.ofInstant(instant, displayZone)).orElse(null);
    }

    private String serialize(CreateTransaction template) {
        try {
            return objectMapper.writeValueAsString(template);
        } catch (JsonProcessingException e) {
            throw new ValidationException("Không lưu được mẫu giao dịch của quy luật định kỳ");
        }
    }
}
