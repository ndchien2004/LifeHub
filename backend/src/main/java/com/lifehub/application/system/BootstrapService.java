package com.lifehub.application.system;

import com.lifehub.application.calendar.ReminderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Assembles the startup payload described in 06-API-SPEC.md 2. */
@Service
public class BootstrapService {

    private static final Logger log = LoggerFactory.getLogger(BootstrapService.class);

    private final SettingService settingService;
    private final ReminderService reminderService;
    private final DashboardService dashboardService;

    public BootstrapService(
            SettingService settingService,
            ReminderService reminderService,
            DashboardService dashboardService) {
        this.settingService = settingService;
        this.reminderService = reminderService;
        this.dashboardService = dashboardService;
    }

    @Transactional(readOnly = true)
    public BootstrapData load() {
        return new BootstrapData(
                settingService.findAll(), false, reminderService.findMissed(), dashboard());
    }

    /**
     * Dashboard figures, or null if they cannot be produced.
     *
     * <p>Bootstrap is the call the whole application waits on at startup. A failure while
     * aggregating summary numbers must degrade to an empty panel, not to an app that will not
     * open - the frontend already renders a placeholder for a null dashboard.
     */
    private DashboardData dashboard() {
        try {
            return dashboardService.load();
        } catch (RuntimeException e) {
            log.warn("Không tổng hợp được số liệu dashboard", e);
            return null;
        }
    }
}
