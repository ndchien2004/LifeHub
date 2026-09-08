package com.lifehub.domain.finance;

import com.lifehub.domain.common.BaseEntity;
import com.lifehub.domain.common.ValidationException;
import com.lifehub.domain.task.Tag;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * One movement of money (FR-FIN-04 → FR-FIN-07).
 *
 * <p>Named {@code MoneyTransaction} rather than {@code Transaction} because the table is the SQL
 * keyword {@code transaction} and because the bare name collides with Spring's own transaction
 * types on every import (03-DATA-MODEL.md 2.8).
 *
 * <p>The three shape rules — a positive amount, a transfer needing a different destination wallet,
 * and a non-transfer needing a category of the matching type — are enforced here as well as by
 * CHECK constraints. The database guarantees the file on disk can never hold a broken row; this
 * class guarantees the user gets a Vietnamese sentence explaining what to fix instead of a
 * constraint violation stack trace.
 */
@Entity
@Table(name = "\"transaction\"")
public class MoneyTransaction extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "wallet_id", nullable = false)
    private Wallet wallet;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "to_wallet_id")
    private Wallet toWallet;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private Category category;

    /**
     * Held as a bare identifier rather than an association: {@code RecurringRule} lives in the
     * system module, and a mapped relation would make the finance entity graph depend on it in
     * both directions for a link that is only ever read as provenance.
     */
    @Column(name = "recurring_rule_id")
    private String recurringRuleId;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false)
    private TransactionType type = TransactionType.EXPENSE;

    /**
     * Stored as a plain {@code long} rather than as a converted {@link Money}.
     *
     * <p>{@code Money} is the type every service, command and test works with - it is what makes a
     * negative or fractional amount unrepresentable. But the column has to stay a bare integer that
     * {@code SUM}, {@code CASE} and range comparisons can operate on directly, and the balance and
     * summary queries depend on exactly that. Keeping the value object at the API of this class and
     * the primitive in the column gives both, with the conversion in one place.
     */
    @Column(name = "amount", nullable = false)
    private long amount;

    @Column(name = "note")
    private String note;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false)
    private TransactionSource source = TransactionSource.MANUAL;

    @Column(name = "ai_confidence")
    private Double aiConfidence;

    @Column(name = "import_hash")
    private String importHash;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @ManyToMany(fetch = FetchType.LAZY)
    @org.hibernate.annotations.BatchSize(size = 200)
    @JoinTable(
            name = "transaction_tag",
            joinColumns = @JoinColumn(name = "transaction_id"),
            inverseJoinColumns = @JoinColumn(name = "tag_id"))
    private Set<Tag> tags = new LinkedHashSet<>();

    protected MoneyTransaction() {
    }

    public MoneyTransaction(
            TransactionType type,
            Money amount,
            Wallet wallet,
            Wallet toWallet,
            Category category,
            Instant occurredAt) {
        this.type = type == null ? TransactionType.EXPENSE : type;
        changeAmount(amount);
        moveTo(wallet, toWallet);
        classifyAs(category);
        reschedule(occurredAt);
    }

    public void changeAmount(Money amount) {
        if (amount == null || !amount.isPositive()) {
            throw new ValidationException("Số tiền phải lớn hơn 0", "amount");
        }
        this.amount = amount.toLong();
    }

    /**
     * Sets the source wallet and, for a transfer, the destination.
     *
     * <p>Both arguments are set together because they are not independent: whether a destination is
     * required, forbidden or merely different from the source depends entirely on the pair.
     */
    public void moveTo(Wallet wallet, Wallet toWallet) {
        if (wallet == null) {
            throw new ValidationException("Giao dịch phải thuộc về một ví", "walletId");
        }
        if (type.isTransfer()) {
            if (toWallet == null) {
                throw new ValidationException("Giao dịch chuyển khoản cần ví đích", "toWalletId");
            }
            if (wallet.getId().equals(toWallet.getId())) {
                throw new ValidationException("Ví nguồn và ví đích phải khác nhau", "toWalletId");
            }
            this.toWallet = toWallet;
        } else {
            this.toWallet = null;
        }
        this.wallet = wallet;
    }

    /** Attaches a category, or clears it for a transfer (FR-FIN-05). */
    public void classifyAs(Category category) {
        if (type.isTransfer()) {
            this.category = null;
            return;
        }
        if (category == null) {
            throw new ValidationException("Giao dịch thu/chi cần danh mục", "categoryId");
        }
        if (category.getType() != type.requiredCategoryType()) {
            throw new ValidationException(
                    "Danh mục không khớp với loại giao dịch", "categoryId");
        }
        this.category = category;
    }

    /**
     * Switches the direction of an existing transaction.
     *
     * <p>Re-runs the wallet and category rules afterwards, because what was valid for an expense is
     * not necessarily valid for a transfer.
     */
    public void changeType(TransactionType type, Wallet toWallet, Category category) {
        if (type == null || type == this.type) {
            return;
        }
        this.type = type;
        moveTo(this.wallet, toWallet);
        classifyAs(category);
    }

    public void reschedule(Instant occurredAt) {
        if (occurredAt == null) {
            throw new ValidationException("Thời điểm giao dịch không được để trống", "occurredAt");
        }
        this.occurredAt = occurredAt;
    }

    public void annotate(String note) {
        this.note = note == null || note.isBlank() ? null : note.trim();
    }

    public void changeSource(TransactionSource source, Double aiConfidence) {
        if (source != null) {
            this.source = source;
        }
        // Only an AI parse can carry a confidence; keeping it on anything else would let a stale
        // value survive an edit and misrepresent a number the user typed by hand.
        this.aiConfidence = this.source == TransactionSource.AI_PARSE ? aiConfidence : null;
    }

    public void markGeneratedBy(String recurringRuleId) {
        this.recurringRuleId = recurringRuleId;
    }

    public void markImportedAs(String importHash) {
        this.importHash = importHash;
    }

    public void replaceTags(Collection<Tag> replacement) {
        tags.clear();
        if (replacement != null) {
            tags.addAll(replacement);
        }
    }

    public void softDelete(Instant now) {
        this.deletedAt = now;
    }

    public void restore() {
        this.deletedAt = null;
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }

    /** Signed effect of this transaction on {@code walletId}, per the formula in 03-DATA-MODEL 2.6. */
    public long effectOn(String walletId) {
        long value = amount;
        boolean isSource = wallet != null && wallet.getId().equals(walletId);
        boolean isDestination = toWallet != null && toWallet.getId().equals(walletId);

        return switch (type) {
            case INCOME -> isSource ? value : 0L;
            case EXPENSE -> isSource ? -value : 0L;
            case TRANSFER -> (isSource ? -value : 0L) + (isDestination ? value : 0L);
        };
    }

    public Wallet getWallet() {
        return wallet;
    }

    public String getWalletId() {
        return wallet == null ? null : wallet.getId();
    }

    public Wallet getToWallet() {
        return toWallet;
    }

    public String getToWalletId() {
        return toWallet == null ? null : toWallet.getId();
    }

    public Category getCategory() {
        return category;
    }

    public String getCategoryId() {
        return category == null ? null : category.getId();
    }

    public String getRecurringRuleId() {
        return recurringRuleId;
    }

    public TransactionType getType() {
        return type;
    }

    public Money getAmount() {
        return Money.of(amount);
    }

    public String getNote() {
        return note;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public TransactionSource getSource() {
        return source;
    }

    public Double getAiConfidence() {
        return aiConfidence;
    }

    public String getImportHash() {
        return importHash;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }

    public Set<Tag> getTags() {
        return tags;
    }
}
