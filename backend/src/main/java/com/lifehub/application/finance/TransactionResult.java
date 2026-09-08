package com.lifehub.application.finance;

import com.lifehub.domain.finance.BudgetAlert;
import com.lifehub.domain.finance.MoneyTransaction;

/**
 * What SD-01 hands back after a write: the row itself, the balances it moved, and any budget
 * threshold it crossed.
 *
 * <p>Returning the recomputed balances with the write saves the frontend a follow-up round trip
 * for the number the user is looking straight at.
 *
 * @param toWalletBalance destination wallet balance, only present for a transfer
 * @param budgetAlert null unless the category is at or above 80% of a budget
 */
public record TransactionResult(
        MoneyTransaction transaction,
        long walletBalance,
        Long toWalletBalance,
        BudgetAlert budgetAlert) {
}
