package com.lifehub.application.system;

import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Assembles the startup payload described in 06-API-SPEC.md 2. */
@Service
public class BootstrapService {

    private final SettingService settingService;

    public BootstrapService(SettingService settingService) {
        this.settingService = settingService;
    }

    @Transactional(readOnly = true)
    public BootstrapData load() {
        return new BootstrapData(settingService.findAll(), false, List.of(), null);
    }
}
