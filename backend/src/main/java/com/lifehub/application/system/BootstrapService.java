package com.lifehub.application.system;

import com.lifehub.application.calendar.ReminderService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Assembles the startup payload described in 06-API-SPEC.md 2. */
@Service
public class BootstrapService {

    private final SettingService settingService;
    private final ReminderService reminderService;

    public BootstrapService(SettingService settingService, ReminderService reminderService) {
        this.settingService = settingService;
        this.reminderService = reminderService;
    }

    @Transactional(readOnly = true)
    public BootstrapData load() {
        return new BootstrapData(
                settingService.findAll(), false, reminderService.findMissed(), null);
    }
}
