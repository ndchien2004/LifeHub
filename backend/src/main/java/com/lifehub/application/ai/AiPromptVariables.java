package com.lifehub.application.ai;

import com.lifehub.domain.ai.ParseContext;
import com.lifehub.domain.ai.ParseContext.ProjectOption;
import com.lifehub.domain.ai.ParseContext.RecentTransaction;
import com.lifehub.domain.ai.ParseContext.WalletOption;
import com.lifehub.domain.finance.CategoryType;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Renders a {@link ParseContext} into the placeholders the prompt templates expect.
 *
 * <p>Everything is a plain list of names. The model is never shown an id, so it cannot return one,
 * and every name it does return is matched back against the user's own data by
 * {@code ParseContext}. That is what keeps a hallucinated category from becoming a real one.
 */
final class AiPromptVariables {

    private static final DateTimeFormatter TIMESTAMP =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX");

    private static final Locale VIETNAMESE = Locale.forLanguageTag("vi");

    private AiPromptVariables() {
    }

    /** Variables for {@code nl-parse.txt}. */
    static Map<String, String> forParse(String text, ParseContext context, String retryHint) {
        Map<String, String> variables = new LinkedHashMap<>(base(context));
        variables.put("text", text);
        variables.put("projects", listOrNone(context.projects().stream()
                .map(ProjectOption::name)
                .toList()));
        variables.put("retryHint", retryHint == null ? "" : retryHint);
        return variables;
    }

    /** Variables for {@code category-suggest.txt}. */
    static Map<String, String> forCategorySuggest(
            String note, CategoryType type, ParseContext context) {
        Map<String, String> variables = new LinkedHashMap<>(base(context));
        variables.put("note", note);
        variables.put("type", type == CategoryType.INCOME ? "Thu (INCOME)" : "Chi (EXPENSE)");
        variables.put("categories", categoryList(context, type));
        return variables;
    }

    private static Map<String, String> base(ParseContext context) {
        var now = context.now().atZone(context.zone());

        Map<String, String> variables = new LinkedHashMap<>();
        variables.put("now", TIMESTAMP.format(now));
        variables.put("zone", context.zone().getId());
        variables.put(
                "weekday", now.getDayOfWeek().getDisplayName(TextStyle.FULL, VIETNAMESE));
        variables.put("categories", categoryList(context, null));
        variables.put("wallets", listOrNone(context.wallets().stream()
                .map(AiPromptVariables::walletLine)
                .toList()));
        variables.put("recentTransactions", recentList(context));
        return variables;
    }

    private static String walletLine(WalletOption wallet) {
        return wallet.isDefault() ? wallet.name() + " (mặc định)" : wallet.name();
    }

    private static String categoryList(ParseContext context, CategoryType type) {
        return listOrNone(context.categories().stream()
                .filter(option -> type == null || option.type() == type)
                .map(option -> option.label() + " [" + option.type() + "]")
                .toList());
    }

    /**
     * The recent sample, one line each.
     *
     * <p>Rows with no category are dropped: an uncategorised transaction teaches the model nothing
     * about how this user classifies things, and it still costs prompt tokens.
     */
    private static String recentList(ParseContext context) {
        return listOrNone(context.recentTransactions().stream()
                .filter(row -> row.categoryName() != null)
                .map(AiPromptVariables::recentLine)
                .toList());
    }

    private static String recentLine(RecentTransaction row) {
        return "\"" + row.note() + "\" → " + row.categoryName() + " (" + row.type() + ", "
                + row.amount() + "đ)";
    }

    private static String listOrNone(List<String> lines) {
        if (lines.isEmpty()) {
            return "(chưa có)";
        }
        return lines.stream().map(line -> "- " + line).collect(Collectors.joining("\n"));
    }
}
