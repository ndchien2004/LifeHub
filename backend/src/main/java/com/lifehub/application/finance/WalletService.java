package com.lifehub.application.finance;

import com.lifehub.application.finance.FinanceCommands.CreateWallet;
import com.lifehub.application.finance.FinanceCommands.UpdateWallet;
import com.lifehub.domain.common.ConflictException;
import com.lifehub.domain.common.NotFoundException;
import com.lifehub.domain.finance.TransactionRepository;
import com.lifehub.domain.finance.Wallet;
import com.lifehub.domain.finance.WalletRepository;
import java.time.Clock;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Wallet management (FR-FIN-01). */
@Service
@Transactional
public class WalletService {

    private final WalletRepository walletRepository;
    private final TransactionRepository transactionRepository;
    private final Clock clock;

    public WalletService(
            WalletRepository walletRepository,
            TransactionRepository transactionRepository,
            Clock clock) {
        this.walletRepository = walletRepository;
        this.transactionRepository = transactionRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<Wallet> findAll() {
        return walletRepository.findAll();
    }

    @Transactional(readOnly = true)
    public Wallet findById(String id) {
        return walletRepository
                .findById(id)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy ví"));
    }

    public Wallet create(CreateWallet command) {
        requireNameAvailable(command.name(), null);

        Wallet wallet = new Wallet(
                command.name(),
                command.type(),
                command.initialBalance() == null ? 0L : command.initialBalance(),
                command.currency(),
                command.icon());
        wallet.changeSortOrder(command.sortOrder());

        // The very first wallet becomes the default whether or not the caller asked, so the
        // transaction form always has something to preselect (UC-06 step 2).
        boolean isDefault = Boolean.TRUE.equals(command.isDefault()) || walletRepository.findAll().isEmpty();
        wallet.markDefault(isDefault);

        Wallet saved = walletRepository.save(wallet);
        if (isDefault) {
            walletRepository.clearDefaultExcept(saved.getId());
        }
        return saved;
    }

    public Wallet update(String id, UpdateWallet command) {
        Wallet wallet = findById(id);

        command.name().ifPresent(name -> {
            requireNameAvailable(name, id);
            wallet.rename(name);
        });
        command.type().ifPresent(wallet::changeType);
        command.initialBalance().ifPresent(wallet::changeInitialBalance);
        command.currency().ifPresent(wallet::changeCurrency);
        command.icon().ifPresent(wallet::changeIcon);
        command.sortOrder().ifPresent(wallet::changeSortOrder);
        command.isDefault().ifPresent(value -> wallet.markDefault(Boolean.TRUE.equals(value)));

        Wallet saved = walletRepository.save(wallet);
        if (saved.isDefaultWallet()) {
            walletRepository.clearDefaultExcept(saved.getId());
        }
        return saved;
    }

    /**
     * Soft deletes a wallet, refusing while transactions still reference it.
     *
     * <p>Required by 03-DATA-MODEL.md 6 item C-6. Hiding the wallet would leave every transaction
     * on it pointing at a name the user can no longer see, and the balances of the remaining
     * wallets would still silently include transfers to and from it.
     */
    public void delete(String id) {
        Wallet wallet = findById(id);

        long transactionCount = transactionRepository.countByWallet(id);
        if (transactionCount > 0) {
            throw new ConflictException(
                    "Không xóa được ví vì còn " + transactionCount + " giao dịch. Hãy xóa hoặc chuyển các giao dịch đó trước.");
        }

        wallet.softDelete(clock.instant());
        wallet.markDefault(false);
        walletRepository.save(wallet);
    }

    private void requireNameAvailable(String name, String excludingId) {
        if (name != null && walletRepository.existsByName(name.trim(), excludingId)) {
            throw new ConflictException("Tên ví đã tồn tại", "name");
        }
    }
}
