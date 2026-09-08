package com.lifehub.application.system;

import com.lifehub.domain.system.Setting;
import com.lifehub.domain.system.SettingRepository;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Read and write access to application settings. */
@Service
public class SettingService {

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

    @Transactional
    public void put(String key, String value) {
        Setting setting = settingRepository.findByKey(key).orElseGet(() -> new Setting(key, value));
        setting.setValue(value);
        settingRepository.save(setting);
    }
}
