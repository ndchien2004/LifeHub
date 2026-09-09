package com.lifehub.application.ai;

import com.lifehub.domain.ai.ParseContext;
import com.lifehub.domain.ai.ParseContext.CategoryOption;
import com.lifehub.domain.ai.ParseContext.ProjectOption;
import com.lifehub.domain.ai.ParseContext.RecentTransaction;
import com.lifehub.domain.ai.ParseContext.WalletOption;
import com.lifehub.domain.common.PageRequest;
import com.lifehub.domain.finance.Category;
import com.lifehub.domain.finance.CategoryRepository;
import com.lifehub.domain.finance.MoneyTransaction;
import com.lifehub.domain.finance.TransactionFilter;
import com.lifehub.domain.finance.TransactionRepository;
import com.lifehub.domain.finance.WalletRepository;
import com.lifehub.domain.task.ProjectRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Gathers what a parser needs to know about this user right now (UC-09 step 6).
 *
 * <p>Runs inside one read only transaction and reads every lazy association it needs while it is
 * still open - a category's parent above all. Building the context outside a session and touching
 * {@code category.getParent()} afterwards is exactly the fault that broke sub-category budgets in
 * Phase 3, and {@code open-in-view} is off precisely so that it fails loudly here instead of
 * silently issuing queries from the view layer.
 *
 * <p>The recent transaction sample is capped at {@link ParseContext#MAX_RECENT_TRANSACTIONS} and
 * reduced to three fields per row. That is the whole of AGENTS.md 3.4 rule 6: enough for the model
 * to learn how this user files a "trà sữa", not an export of their finances.
 */
@Service
public class AiContextProvider {

    private final CategoryRepository categoryRepository;
    private final WalletRepository walletRepository;
    private final ProjectRepository projectRepository;
    private final TransactionRepository transactionRepository;
    private final Clock clock;
    private final ZoneId displayZone;

    public AiContextProvider(
            CategoryRepository categoryRepository,
            WalletRepository walletRepository,
            ProjectRepository projectRepository,
            TransactionRepository transactionRepository,
            Clock clock,
            ZoneId displayZone) {
        this.categoryRepository = categoryRepository;
        this.walletRepository = walletRepository;
        this.projectRepository = projectRepository;
        this.transactionRepository = transactionRepository;
        this.clock = clock;
        this.displayZone = displayZone;
    }

    @Transactional(readOnly = true)
    public ParseContext load() {
        return new ParseContext(
                Instant.now(clock),
                displayZone,
                categories(),
                wallets(),
                projects(),
                recentTransactions());
    }

    /** A context with no user data, for parsing that only needs a clock. */
    public ParseContext emptyContext() {
        return ParseContext.empty(Instant.now(clock), displayZone);
    }

    private List<CategoryOption> categories() {
        return categoryRepository.findAll(null).stream()
                .map(this::toOption)
                .toList();
    }

    private CategoryOption toOption(Category category) {
        Category parent = category.getParent();
        return new CategoryOption(
                category.getId(),
                category.getName(),
                parent == null ? null : parent.getName(),
                category.getType());
    }

    private List<WalletOption> wallets() {
        return walletRepository.findAll().stream()
                .map(wallet -> new WalletOption(
                        wallet.getId(), wallet.getName(), wallet.isDefaultWallet()))
                .toList();
    }

    private List<ProjectOption> projects() {
        return projectRepository.findAll(false).stream()
                .map(project -> new ProjectOption(project.getId(), project.getName()))
                .toList();
    }

    private List<RecentTransaction> recentTransactions() {
        PageRequest page = new PageRequest(
                0, ParseContext.MAX_RECENT_TRANSACTIONS, "occurredAt", false);

        return transactionRepository.search(TransactionFilter.empty(), page).items().stream()
                .filter(transaction -> transaction.getNote() != null
                        && !transaction.getNote().isBlank())
                .map(this::toRecent)
                .toList();
    }

    private RecentTransaction toRecent(MoneyTransaction transaction) {
        return new RecentTransaction(
                transaction.getNote(),
                categoryLabel(transaction),
                transaction.getType(),
                transaction.getAmount().toLong());
    }

    /** Reads the category, and its parent, while the session is still open. */
    private String categoryLabel(MoneyTransaction transaction) {
        Category category = transaction.getCategory();
        if (category == null) {
            return null;
        }
        Category parent = category.getParent();
        return parent == null ? category.getName() : parent.getName() + " › " + category.getName();
    }
}
