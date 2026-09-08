package com.lifehub.domain.finance;

/** Direction of a transaction (FR-FIN-04). */
public enum TransactionType {
    INCOME,
    EXPENSE,
    TRANSFER;

    /** A transfer moves money between two wallets, so it needs no category (FR-FIN-05). */
    public boolean isTransfer() {
        return this == TRANSFER;
    }

    /** The category type a non-transfer transaction must be classified with. */
    public CategoryType requiredCategoryType() {
        return this == INCOME ? CategoryType.INCOME : CategoryType.EXPENSE;
    }
}
