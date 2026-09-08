package com.lifehub.domain.finance;

import com.lifehub.domain.common.BaseEntity;
import com.lifehub.domain.common.ValidationException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * An account money sits in: cash, a bank account, an e-wallet or a credit line (FR-FIN-01).
 *
 * <p>The current balance is deliberately <em>not</em> a field. It is derived from
 * {@code initialBalance} plus every transaction touching this wallet, per the formula in
 * 03-DATA-MODEL.md 2.6. A stored balance would have to be kept in step with every edit, restore and
 * delete, and the first missed update would be silent and permanent.
 */
@Entity
@Table(name = "wallet")
@org.hibernate.annotations.BatchSize(size = 100)
public class Wallet extends BaseEntity {

    public static final int MAX_NAME_LENGTH = 100;
    private static final String DEFAULT_CURRENCY = "VND";

    @Column(name = "name", nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false)
    private WalletType type = WalletType.CASH;

    /**
     * Opening balance, which unlike a transaction amount may be negative - a credit card starts
     * life owing money. Stored as a plain long for exactly that reason; {@link Money} is
     * non-negative by design.
     */
    @Column(name = "initial_balance", nullable = false)
    private long initialBalance;

    @Column(name = "currency", nullable = false)
    private String currency = DEFAULT_CURRENCY;

    @Column(name = "icon")
    private String icon;

    @Column(name = "is_default", nullable = false)
    private boolean defaultWallet;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    protected Wallet() {
    }

    public Wallet(String name, WalletType type, long initialBalance, String currency, String icon) {
        rename(name);
        changeType(type);
        this.initialBalance = initialBalance;
        changeCurrency(currency);
        this.icon = icon;
    }

    public void rename(String name) {
        String trimmed = name == null ? "" : name.trim();
        if (trimmed.isEmpty()) {
            throw new ValidationException("Tên ví không được để trống", "name");
        }
        if (trimmed.length() > MAX_NAME_LENGTH) {
            throw new ValidationException("Tên ví tối đa " + MAX_NAME_LENGTH + " ký tự", "name");
        }
        this.name = trimmed;
    }

    public void changeType(WalletType type) {
        if (type != null) {
            this.type = type;
        }
    }

    public void changeInitialBalance(Long initialBalance) {
        if (initialBalance != null) {
            this.initialBalance = initialBalance;
        }
    }

    public void changeCurrency(String currency) {
        this.currency = currency == null || currency.isBlank()
                ? DEFAULT_CURRENCY
                : currency.trim().toUpperCase(java.util.Locale.ROOT);
    }

    public void changeIcon(String icon) {
        this.icon = icon;
    }

    public void markDefault(boolean isDefault) {
        this.defaultWallet = isDefault;
    }

    public void changeSortOrder(Integer sortOrder) {
        if (sortOrder != null) {
            this.sortOrder = sortOrder;
        }
    }

    public void softDelete(Instant now) {
        this.deletedAt = now;
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }

    public String getName() {
        return name;
    }

    public WalletType getType() {
        return type;
    }

    public long getInitialBalance() {
        return initialBalance;
    }

    public String getCurrency() {
        return currency;
    }

    public String getIcon() {
        return icon;
    }

    public boolean isDefaultWallet() {
        return defaultWallet;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }
}
