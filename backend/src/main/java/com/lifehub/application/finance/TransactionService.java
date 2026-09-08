package com.lifehub.application.finance;

import com.lifehub.application.finance.FinanceCommands.CreateTransaction;
import com.lifehub.application.finance.FinanceCommands.UpdateTransaction;
import com.lifehub.domain.finance.BudgetAlert;
import com.lifehub.domain.finance.MoneyTransaction;
import com.lifehub.domain.finance.Wallet;
import com.lifehub.domain.finance.WalletRepository;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Writing a transaction, end to end (SD-01, UC-06).
 *
 * <p>Intentionally carries no {@code @Transactional} annotation. The row is written by
 * {@link TransactionWriter} in its own transaction, which commits before this class recalculates
 * balances and checks budgets. SD-01 is explicit that those two steps sit outside the write: they
 * exist to decorate the response, and a failure in either must never cost the user the transaction
 * they just entered. Both are therefore also wrapped so an unexpected error degrades the response
 * rather than the request.
 *
 * <p>Only the wallets actually touched are recalculated, never all of them.
 */
@Service
public class TransactionService {

    private static final Logger log = LoggerFactory.getLogger(TransactionService.class);

    private final TransactionWriter writer;
    private final WalletBalanceCalculator balanceCalculator;
    private final BudgetService budgetService;
    private final WalletRepository walletRepository;

    public TransactionService(
            TransactionWriter writer,
            WalletBalanceCalculator balanceCalculator,
            BudgetService budgetService,
            WalletRepository walletRepository) {
        this.writer = writer;
        this.balanceCalculator = balanceCalculator;
        this.budgetService = budgetService;
        this.walletRepository = walletRepository;
    }

    public TransactionResult create(CreateTransaction command) {
        return decorate(writer.write(command), true);
    }

    public TransactionResult update(String id, UpdateTransaction command) {
        return decorate(writer.rewrite(id, command), true);
    }

    /**
     * Soft deletes a transaction and reports the restored balances (FR-FIN-04).
     *
     * <p>No budget alert: removing spending can only lower usage, and a warning banner on a delete
     * would be noise.
     */
    public TransactionResult delete(String id) {
        return decorate(writer.softDelete(id), false);
    }

    public TransactionResult restore(String id) {
        return decorate(writer.restore(id), true);
    }

    private TransactionResult decorate(MoneyTransaction transaction, boolean checkBudget) {
        long walletBalance = balanceOf(transaction.getWalletId());
        Long toWalletBalance =
                transaction.getToWalletId() == null ? null : balanceOf(transaction.getToWalletId());

        BudgetAlert alert = null;
        if (checkBudget && transaction.getCategoryId() != null) {
            try {
                alert = budgetService.alertFor(transaction.getCategoryId(), transaction.getOccurredAt());
            } catch (RuntimeException e) {
                log.warn("Không kiểm tra được ngưỡng ngân sách cho giao dịch {}", transaction.getId(), e);
            }
        }

        return new TransactionResult(transaction, walletBalance, toWalletBalance, alert);
    }

    private long balanceOf(String walletId) {
        if (walletId == null) {
            return 0L;
        }
        try {
            Optional<Wallet> wallet = walletRepository.findById(walletId);
            return wallet.map(w -> balanceCalculator.balanceOf(w).balance()).orElse(0L);
        } catch (RuntimeException e) {
            log.warn("Không tính lại được số dư ví {}", walletId, e);
            return 0L;
        }
    }
}
