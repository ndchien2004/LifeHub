package com.lifehub.application.finance;

import com.lifehub.application.finance.FinanceCommands.CreateTransaction;
import com.lifehub.application.finance.FinanceCommands.UpdateTransaction;
import com.lifehub.domain.common.NotFoundException;
import com.lifehub.domain.finance.Category;
import com.lifehub.domain.finance.CategoryRepository;
import com.lifehub.domain.finance.Money;
import com.lifehub.domain.finance.MoneyTransaction;
import com.lifehub.domain.finance.TransactionRepository;
import com.lifehub.domain.finance.TransactionType;
import com.lifehub.domain.finance.Wallet;
import com.lifehub.domain.finance.WalletRepository;
import com.lifehub.domain.task.Tag;
import com.lifehub.domain.task.TagRepository;
import java.time.Clock;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The database-writing half of the transaction module (SD-01 steps 45-49).
 *
 * <p>Split out from {@link TransactionService} for one reason: SD-01 requires the balance
 * recalculation and budget check to run <em>outside</em> the transaction that writes the row, so
 * that a failure in either still leaves the user's transaction saved. If the whole flow shared one
 * {@code @Transactional} method, an exception while computing a warning banner would silently roll
 * back the record the user just entered - the exact outcome the diagram calls out.
 */
@Service
@Transactional
public class TransactionWriter {

    private final TransactionRepository transactionRepository;
    private final WalletRepository walletRepository;
    private final CategoryRepository categoryRepository;
    private final TagRepository tagRepository;
    private final Clock clock;

    public TransactionWriter(
            TransactionRepository transactionRepository,
            WalletRepository walletRepository,
            CategoryRepository categoryRepository,
            TagRepository tagRepository,
            Clock clock) {
        this.transactionRepository = transactionRepository;
        this.walletRepository = walletRepository;
        this.categoryRepository = categoryRepository;
        this.tagRepository = tagRepository;
        this.clock = clock;
    }

    public MoneyTransaction write(CreateTransaction command) {
        TransactionType type = command.type() == null ? TransactionType.EXPENSE : command.type();

        MoneyTransaction transaction = new MoneyTransaction(
                type,
                Money.of(command.amount()),
                requireWallet(command.walletId()),
                type.isTransfer() ? requireWallet(command.toWalletId()) : null,
                type.isTransfer() ? null : requireCategory(command.categoryId()),
                command.occurredAt() == null ? clock.instant() : command.occurredAt());

        transaction.annotate(command.note());
        transaction.changeSource(command.source(), command.aiConfidence());
        transaction.markGeneratedBy(command.recurringRuleId());
        transaction.markImportedAs(command.importHash());
        transaction.replaceTags(resolveTags(command.tagIds()));

        return hydrate(transactionRepository.save(transaction));
    }

    public MoneyTransaction rewrite(String id, UpdateTransaction command) {
        MoneyTransaction transaction = require(id);

        // Type has to move first: whether a destination wallet is required and whether a category
        // is allowed both depend on it, so applying the other fields first would validate them
        // against the old shape.
        if (command.type().present() && command.type().value() != null) {
            TransactionType target = command.type().value();
            transaction.changeType(
                    target,
                    target.isTransfer()
                            ? requireWallet(command.toWalletId().orElse(transaction.getToWalletId()))
                            : null,
                    target.isTransfer()
                            ? null
                            : requireCategory(command.categoryId().orElse(transaction.getCategoryId())));
        }

        command.amount().ifPresent(amount -> transaction.changeAmount(Money.of(amount)));
        command.occurredAt().ifPresent(transaction::reschedule);
        command.note().ifPresent(transaction::annotate);

        if (command.walletId().present() || command.toWalletId().present()) {
            Wallet source = command.walletId().present()
                    ? requireWallet(command.walletId().value())
                    : transaction.getWallet();
            String destinationId = command.toWalletId().orElse(transaction.getToWalletId());
            transaction.moveTo(
                    source,
                    transaction.getType().isTransfer() ? requireWallet(destinationId) : null);
        }

        if (command.categoryId().present() && !transaction.getType().isTransfer()) {
            transaction.classifyAs(requireCategory(command.categoryId().value()));
        }

        command.tagIds().ifPresent(tagIds -> transaction.replaceTags(resolveTags(tagIds)));

        return hydrate(transactionRepository.save(transaction));
    }

    public MoneyTransaction softDelete(String id) {
        MoneyTransaction transaction = require(id);
        transaction.softDelete(clock.instant());
        return hydrate(transactionRepository.save(transaction));
    }

    public MoneyTransaction restore(String id) {
        MoneyTransaction transaction = transactionRepository
                .findByIdIncludingDeleted(id)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy giao dịch"));
        transaction.restore();
        return hydrate(transactionRepository.save(transaction));
    }

    /**
     * Touches every association the response will render, while the session is still open.
     *
     * <p>{@code open-in-view} is off and the caller runs outside this transaction by design (see
     * the class comment), so a lazy proxy left untouched here would blow up in the mapper rather
     * than here. The category parent matters as much as the category itself: the transaction list
     * shows "Ăn uống › Cà phê".
     */
    private MoneyTransaction hydrate(MoneyTransaction transaction) {
        if (transaction.getWallet() != null) {
            transaction.getWallet().getName();
        }
        if (transaction.getToWallet() != null) {
            transaction.getToWallet().getName();
        }
        Category category = transaction.getCategory();
        if (category != null) {
            category.getName();
            if (category.getParent() != null) {
                category.getParent().getName();
            }
        }
        transaction.getTags().size();
        return transaction;
    }

    private MoneyTransaction require(String id) {
        return transactionRepository
                .findById(id)
                .orElseThrow(() -> new NotFoundException("Không tìm thấy giao dịch"));
    }

    private Wallet requireWallet(String walletId) {
        if (walletId == null || walletId.isBlank()) {
            throw new NotFoundException("Không tìm thấy ví");
        }
        return walletRepository
                .findById(walletId)
                .orElseThrow(() -> new NotFoundException("Ví không tồn tại"));
    }

    private Category requireCategory(String categoryId) {
        if (categoryId == null || categoryId.isBlank()) {
            return null;
        }
        return categoryRepository
                .findById(categoryId)
                .orElseThrow(() -> new NotFoundException("Danh mục không tồn tại"));
    }

    private List<Tag> resolveTags(List<String> tagIds) {
        if (tagIds == null || tagIds.isEmpty()) {
            return List.of();
        }
        return List.copyOf(tagRepository.findAllById(new java.util.LinkedHashSet<>(tagIds)));
    }
}
