package com.lifehub.application.finance;

import com.lifehub.domain.finance.TransactionRepository;
import com.lifehub.domain.finance.Wallet;
import com.lifehub.domain.finance.WalletBalance;
import com.lifehub.domain.finance.WalletRepository;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Derives wallet balances from the ledger (FR-FIN-07, 03-DATA-MODEL.md 2.6).
 *
 * <pre>
 * balance = initial_balance
 *         + SUM(INCOME into this wallet)
 *         - SUM(EXPENSE from this wallet)
 *         - SUM(TRANSFER with wallet_id = this wallet)
 *         + SUM(TRANSFER with to_wallet_id = this wallet)
 * </pre>
 *
 * <p>Nothing is cached and no balance is stored. Editing an amount, soft deleting a row or
 * restoring one therefore needs no compensating update anywhere - the next read simply computes
 * the new answer. The sums are done in SQL, so the cost does not grow with the size of the ledger.
 */
@Service
@Transactional(readOnly = true)
public class WalletBalanceCalculator {

    private final WalletRepository walletRepository;
    private final TransactionRepository transactionRepository;

    public WalletBalanceCalculator(
            WalletRepository walletRepository, TransactionRepository transactionRepository) {
        this.walletRepository = walletRepository;
        this.transactionRepository = transactionRepository;
    }

    /** Balance of one wallet right now. */
    public WalletBalance balanceOf(Wallet wallet) {
        return balanceOf(wallet, null);
    }

    /**
     * Balance of one wallet as it stood at {@code asOf}.
     *
     * @param asOf exclusive upper bound on {@code occurredAt}, or null for the current balance
     */
    public WalletBalance balanceOf(Wallet wallet, Instant asOf) {
        TransactionRepository.Movement movement =
                transactionRepository.movementFor(wallet.getId(), asOf);
        return WalletBalance.of(
                wallet.getId(), wallet.getInitialBalance(), movement.received(), movement.spent());
    }

    /** Balances for a whole list of wallets in a single aggregate query. */
    public Map<String, WalletBalance> balancesOf(List<Wallet> wallets) {
        return balancesOf(wallets, null);
    }

    public Map<String, WalletBalance> balancesOf(List<Wallet> wallets, Instant asOf) {
        Map<String, TransactionRepository.Movement> movements =
                transactionRepository.movementForAll(asOf);

        Map<String, WalletBalance> balances = new HashMap<>();
        for (Wallet wallet : wallets) {
            TransactionRepository.Movement movement =
                    movements.getOrDefault(wallet.getId(), TransactionRepository.Movement.NONE);
            balances.put(
                    wallet.getId(),
                    WalletBalance.of(
                            wallet.getId(),
                            wallet.getInitialBalance(),
                            movement.received(),
                            movement.spent()));
        }
        return balances;
    }

    /** Sum of every wallet's balance, shown as total net worth on the wallet screen. */
    public long totalAssets() {
        return balancesOf(walletRepository.findAll()).values().stream()
                .mapToLong(WalletBalance::balance)
                .sum();
    }
}
