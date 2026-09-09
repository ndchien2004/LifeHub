package com.lifehub.application.system;

import com.lifehub.domain.common.ValidationException;
import com.lifehub.domain.system.Setting;
import com.lifehub.domain.system.SettingRepository;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read and write access to application settings (FR-SYS-09).
 *
 * <p>Writes go through a whitelist. The table is a plain key/value store, so without one a typo in
 * the Settings screen would quietly create a setting nothing reads, and the user would be left
 * looking at a value that has no effect. An unknown key is rejected outright.
 *
 * <p>The API key is deliberately absent from this table and from this class - it lives in the
 * operating system credential store (03-DATA-MODEL.md 2.11).
 */
@Service
public class SettingService {

    public static final String THEME = "app.theme";
    public static final String TIMEZONE = "app.timezone";
    public static final String WEEK_START = "app.week_start";
    public static final String CURRENCY = "app.currency";
    public static final String AI_ENABLED = "ai.enabled";
    public static final String AI_MODEL = "ai.model";
    public static final String WEEKLY_INSIGHT_CRON = "ai.weekly_insight_cron";
    public static final String BACKUP_DIR = "backup.dir";
    public static final String BACKUP_KEEP_COUNT = "backup.keep_count";

    /**
     * Settings the user may change, each with the rule its value has to satisfy.
     *
     * <p>{@code db.schema_version} is not here on purpose: Flyway owns it, and letting the Settings
     * screen write it would let a user tell the application it had run a migration it had not.
     */
    private static final Map<String, Consumer<String>> WRITABLE = writable();

    private final SettingRepository settingRepository;

    public SettingService(SettingRepository settingRepository) {
        this.settingRepository = settingRepository;
    }

    /** All settings as a key to value map, ordered by key. */
    @Transactional(readOnly = true)
    public Map<String, String> findAll() {
        Map<String, String> settings = new LinkedHashMap<>();
        for (Setting setting : settingRepository.findAll()) {
            settings.put(setting.getKey(), setting.getValue());
        }
        return settings;
    }

    @Transactional(readOnly = true)
    public String get(String key, String defaultValue) {
        return settingRepository.findByKey(key).map(Setting::getValue).orElse(defaultValue);
    }

    /** Writes one setting after checking the key is writable and the value is legal. */
    @Transactional
    public void put(String key, String value) {
        validate(key, value);
        write(key, value);
    }

    /**
     * Writes several settings in one transaction ({@code PUT /settings}).
     *
     * <p>All or nothing: every value is validated before any is written, so a screen that submits
     * five fields with one bad timezone leaves the other four alone rather than half applying.
     */
    @Transactional
    public Map<String, String> putAll(Map<String, String> updates) {
        if (updates == null || updates.isEmpty()) {
            throw new ValidationException("Không có cài đặt nào để cập nhật");
        }
        updates.forEach(this::validate);
        updates.forEach(this::write);
        return findAll();
    }

    /**
     * Whether changing these keys needs the backend restarted to take effect.
     *
     * <p>The display timezone is resolved once at startup ({@code TimeConfig}) and the API key only
     * reaches the process through its environment, so both are read exactly once. The Settings
     * screen uses this to offer the restart rather than leaving the user to discover that their
     * change did nothing.
     */
    public static boolean requiresRestart(Set<String> keys) {
        return keys.contains(TIMEZONE);
    }

    private void write(String key, String value) {
        Setting setting = settingRepository.findByKey(key).orElseGet(() -> new Setting(key, value));
        setting.setValue(value);
        settingRepository.save(setting);
    }

    private void validate(String key, String value) {
        Consumer<String> rule = WRITABLE.get(key);
        if (rule == null) {
            throw new ValidationException("Cài đặt không hợp lệ: " + key, key);
        }
        if (value == null) {
            throw new ValidationException("Giá trị không được để trống", key);
        }
        rule.accept(value.trim());
    }

    private static Map<String, Consumer<String>> writable() {
        Map<String, Consumer<String>> rules = new LinkedHashMap<>();
        rules.put(THEME, oneOf(THEME, List.of("LIGHT", "DARK", "SYSTEM")));
        rules.put(TIMEZONE, SettingService::validateTimezone);
        rules.put(WEEK_START, oneOf(WEEK_START, List.of("MONDAY", "SUNDAY")));
        rules.put(CURRENCY, nonBlank(CURRENCY, "Đơn vị tiền tệ không được để trống"));
        rules.put(AI_ENABLED, oneOf(AI_ENABLED, List.of("true", "false")));
        rules.put(AI_MODEL, nonBlank(AI_MODEL, "Tên model không được để trống"));
        rules.put(WEEKLY_INSIGHT_CRON, SettingService::validateCron);
        rules.put(BACKUP_DIR, nonBlank(BACKUP_DIR, "Thư mục backup không được để trống"));
        rules.put(BACKUP_KEEP_COUNT, SettingService::validateKeepCount);
        return Map.copyOf(rules);
    }

    private static Consumer<String> oneOf(String key, List<String> allowed) {
        return value -> {
            if (allowed.stream().noneMatch(option -> option.equalsIgnoreCase(value))) {
                throw new ValidationException(
                        "Giá trị phải là một trong: " + String.join(", ", allowed), key);
            }
        };
    }

    private static Consumer<String> nonBlank(String key, String message) {
        return value -> {
            if (value.isBlank()) {
                throw new ValidationException(message, key);
            }
        };
    }

    private static void validateTimezone(String value) {
        try {
            ZoneId.of(value);
        } catch (RuntimeException e) {
            throw new ValidationException("Múi giờ không hợp lệ: " + value, TIMEZONE);
        }
    }

    private static void validateCron(String value) {
        if (!CronExpression.isValidExpression(value)) {
            throw new ValidationException("Biểu thức cron không hợp lệ", WEEKLY_INSIGHT_CRON);
        }
    }

    private static void validateKeepCount(String value) {
        int count;
        try {
            count = Integer.parseInt(value);
        } catch (NumberFormatException e) {
            throw new ValidationException("Số bản backup phải là số nguyên", BACKUP_KEEP_COUNT);
        }
        if (count < 1 || count > 365) {
            throw new ValidationException("Số bản backup phải từ 1 đến 365", BACKUP_KEEP_COUNT);
        }
    }
}
