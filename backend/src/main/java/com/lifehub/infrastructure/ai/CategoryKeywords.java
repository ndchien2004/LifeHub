package com.lifehub.infrastructure.ai;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Keyword to category lookup for the offline parser (04-ARCHITECTURE.md 7, last table row).
 *
 * <p>The target names are the seeded Vietnamese categories from {@code V5__seed_categories.sql}.
 * Nothing here resolves an id: {@code ParseContext} does that, matching by name, so a user who
 * renamed "Cà phê" to "Coffee" simply stops matching that rule rather than getting a suggestion
 * pointing at a category they no longer have.
 *
 * <p>Entries are ordered from most specific to least, and the first hit wins - "cà phê" has to be
 * tried before "ăn" or a coffee would be filed as a meal.
 */
final class CategoryKeywords {

    /** Category name to the folded keywords that select it, most specific category first. */
    private static final Map<String, List<String>> EXPENSE = expense();

    private static final Map<String, List<String>> INCOME = income();

    /** Words that make a sentence describe money coming in rather than going out. */
    private static final List<String> INCOME_MARKERS =
            List.of("luong", "nhan duoc", "nhan tien", "thuong", "hoan tien", "tien thuong",
                    "co tuc", "lai suat", "ban duoc", "thu nhap");

    private CategoryKeywords() {
    }

    /** Whether the sentence describes income (spec: keywords lương, nhận, thưởng, hoàn tiền). */
    static boolean looksLikeIncome(String folded) {
        return INCOME_MARKERS.stream().anyMatch(folded::contains);
    }

    /** The category name the sentence points at, or empty when no keyword matches. */
    static Optional<String> categoryFor(String folded, boolean income) {
        Map<String, List<String>> table = income ? INCOME : EXPENSE;
        for (Map.Entry<String, List<String>> entry : table.entrySet()) {
            for (String keyword : entry.getValue()) {
                if (folded.contains(keyword)) {
                    return Optional.of(entry.getKey());
                }
            }
        }
        return Optional.empty();
    }

    private static Map<String, List<String>> expense() {
        Map<String, List<String>> map = new LinkedHashMap<>();
        map.put("Cà phê", List.of("ca phe", "cafe", "coffee", "tra sua", "highlands", "starbucks"));
        map.put("Đi chợ", List.of("di cho", "sieu thi", "bach hoa", "rau cu", "thit ca"));
        map.put("Xăng xe", List.of("xang", "do xang", "nhien lieu"));
        map.put("Grab/Taxi", List.of("grab", "taxi", "gojek", "be xe", "xe om"));
        map.put("Gửi xe", List.of("gui xe", "do xe", "ve xe thang"));
        map.put("Tiền nhà", List.of("tien nha", "thue nha", "thue phong", "tien tro"));
        map.put("Điện nước", List.of("tien dien", "tien nuoc", "hoa don dien", "dien nuoc"));
        map.put("Internet", List.of("internet", "wifi", "cuoc mang", "fpt", "cap quang"));
        map.put("Quần áo", List.of("quan ao", "ao khoac", "giay dep", "vay dam"));
        map.put("Đồ điện tử", List.of("dien thoai", "laptop", "tai nghe", "ban phim", "chuot may"));
        map.put("Đồ dùng", List.of("do dung", "gia dung", "chen bat", "bot giat"));
        map.put("Sức khỏe", List.of("thuoc", "kham benh", "benh vien", "bac si", "nha khoa", "phong kham"));
        map.put("Giải trí", List.of("xem phim", "rap phim", "game", "du lich", "karaoke", "ca nhac"));
        map.put("Học tập", List.of("hoc phi", "khoa hoc", "sach", "lop hoc", "gia su"));
        map.put("Subscription", List.of("netflix", "spotify", "youtube premium", "goi cuoc", "subscription"));
        map.put("Ăn uống", List.of("an trua", "an sang", "an toi", "com", "pho", "bun", "banh mi",
                "nha hang", "quan an", "do an", "an uong", "nhau", "lau"));
        return java.util.Collections.unmodifiableMap(map);
    }

    private static Map<String, List<String>> income() {
        Map<String, List<String>> map = new LinkedHashMap<>();
        map.put("Thưởng", List.of("thuong", "tien thuong", "bonus"));
        map.put("Freelance", List.of("freelance", "lam them", "du an ngoai"));
        map.put("Đầu tư", List.of("co tuc", "lai suat", "dau tu", "chung khoan", "tiet kiem"));
        map.put("Lương", List.of("luong", "thu nhap"));
        return java.util.Collections.unmodifiableMap(map);
    }
}
