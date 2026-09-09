package com.lifehub.domain.ai;

import com.lifehub.domain.finance.CategoryType;
import com.lifehub.domain.finance.TransactionType;
import java.text.Normalizer;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Everything a parser needs to know about this particular user, at this particular moment.
 *
 * <p>Two jobs. It goes into the prompt, so the model proposes categories and wallets that actually
 * exist rather than inventing plausible names (UC-09 step 6). And it resolves the names that come
 * back into real ids here on the backend - the model never sees an id, so it can never return one
 * that points at someone's data by accident.
 *
 * <p>Name matching is accent and case insensitive because the model may well answer "An uong" for a
 * category the user called "Ăn uống", and because a user typing quickly rarely types the diacritics.
 *
 * @param recentTransactions at most the 30 most recent, per AGENTS.md 3.4 rule 6 - enough to teach
 *     the model this user's habits, not enough to be an export of their finances
 */
public record ParseContext(
        Instant now,
        ZoneId zone,
        List<CategoryOption> categories,
        List<WalletOption> wallets,
        List<ProjectOption> projects,
        List<RecentTransaction> recentTransactions) {

    /** Above this many characters in common, two notes are treated as the same kind of spending. */
    public static final int MAX_RECENT_TRANSACTIONS = 30;

    public ParseContext {
        categories = categories == null ? List.of() : List.copyOf(categories);
        wallets = wallets == null ? List.of() : List.copyOf(wallets);
        projects = projects == null ? List.of() : List.copyOf(projects);
        recentTransactions =
                recentTransactions == null ? List.of() : List.copyOf(recentTransactions);
    }

    /** An empty context anchored at a point in time, for parsing that needs no user data. */
    public static ParseContext empty(Instant now, ZoneId zone) {
        return new ParseContext(now, zone, List.of(), List.of(), List.of(), List.of());
    }

    /**
     * Finds the category the model named, preferring one of the right income/expense type.
     *
     * <p>Falls back to a match of any type so that a model answering "Lương" for what it labelled an
     * expense still lands on a real category rather than none at all; the user sees and corrects the
     * type on the form either way.
     */
    public Optional<CategoryOption> findCategory(String name, CategoryType type) {
        if (isBlank(name)) {
            return Optional.empty();
        }
        String needle = normalize(name);
        return categories.stream()
                .filter(option -> type == null || option.type() == type)
                .filter(option -> normalize(option.name()).equals(needle))
                .findFirst()
                .or(() -> categories.stream()
                        .filter(option -> normalize(option.name()).equals(needle))
                        .findFirst());
    }

    public Optional<WalletOption> findWallet(String name) {
        if (isBlank(name)) {
            return Optional.empty();
        }
        String needle = normalize(name);
        return wallets.stream()
                .filter(option -> normalize(option.name()).equals(needle))
                .findFirst();
    }

    public Optional<ProjectOption> findProject(String name) {
        if (isBlank(name)) {
            return Optional.empty();
        }
        String needle = normalize(name);
        return projects.stream()
                .filter(option -> normalize(option.name()).equals(needle))
                .findFirst();
    }

    /** The wallet a transaction form would preselect, so a draft matches what the user expects. */
    public Optional<WalletOption> defaultWallet() {
        return wallets.stream()
                .filter(WalletOption::isDefault)
                .findFirst()
                .or(() -> wallets.stream().findFirst());
    }

    /**
     * Lowercased and stripped of diacritics, for comparing two pieces of Vietnamese text.
     *
     * <p>NFD splits "ă" into "a" plus a combining breve, which the character class then removes.
     * "đ" carries no combining mark and survives decomposition, so it is replaced by hand.
     */
    public static String normalize(String value) {
        if (value == null) {
            return "";
        }
        String lower = value.toLowerCase(Locale.ROOT).trim();
        String decomposed = Normalizer.normalize(lower, Normalizer.Form.NFD);
        return decomposed
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "")
                .replace('đ', 'd')
                .replaceAll("\\s+", " ");
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    /**
     * One category the user owns.
     *
     * @param parentName the parent's name for a second level category, null at the root - the model
     *     is shown "Ăn uống › Cà phê" so it can tell two similarly named leaves apart
     */
    public record CategoryOption(String id, String name, String parentName, CategoryType type) {

        /** The label shown to the model and in a suggestion chip. */
        public String label() {
            return parentName == null ? name : parentName + " › " + name;
        }
    }

    public record WalletOption(String id, String name, boolean isDefault) {
    }

    public record ProjectOption(String id, String name) {
    }

    /**
     * One past transaction, reduced to the three fields that teach the model anything.
     *
     * <p>No id, no wallet, no exact timestamp: a few-shot example only needs to show that this user
     * files "trà sữa" under "Cà phê". Sending more would be sending financial history for no gain
     * (AGENTS.md 3.4 rule 6).
     */
    public record RecentTransaction(
            String note, String categoryName, TransactionType type, long amount) {
    }
}
