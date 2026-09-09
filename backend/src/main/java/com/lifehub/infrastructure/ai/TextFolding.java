package com.lifehub.infrastructure.ai;

import java.text.Normalizer;
import java.util.Locale;

/**
 * Lowercases Vietnamese text and removes its diacritics <em>without changing its length</em>.
 *
 * <p>{@code ParseContext.normalize} also folds accents, but it collapses whitespace, so an index
 * into its output no longer points at the same character in the original. The rule based parser
 * needs both: it matches patterns against folded text, then cuts the matched ranges out of the
 * user's original sentence to build a title. That only works if one character in equals one
 * character out.
 *
 * <p>Users type "thu 5 tuan sau" as often as "thứ 5 tuần sau", so folding is what lets one set of
 * patterns handle both.
 */
final class TextFolding {

    private TextFolding() {
    }

    static String fold(String text) {
        if (text == null) {
            return "";
        }

        String lower = text.toLowerCase(Locale.ROOT);
        StringBuilder folded = new StringBuilder(lower.length());

        for (int i = 0; i < lower.length(); i++) {
            char c = lower.charAt(i);
            if (c == 'đ') {
                // Carries no combining mark, so decomposition leaves it untouched.
                folded.append('d');
                continue;
            }
            String decomposed = Normalizer.normalize(String.valueOf(c), Normalizer.Form.NFD);
            char base = decomposed.charAt(0);
            // A decomposed character always starts with its base letter; the marks follow it.
            folded.append(base == 'đ' ? 'd' : base);
        }
        return folded.toString();
    }
}
