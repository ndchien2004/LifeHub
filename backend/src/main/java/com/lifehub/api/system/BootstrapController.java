package com.lifehub.api.system;

import com.lifehub.api.common.ApiResponse;
import com.lifehub.application.system.BootstrapData;
import com.lifehub.application.system.BootstrapService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Single startup call that primes the renderer (06-API-SPEC.md 2). */
@RestController
@RequestMapping("/api/v1")
public class BootstrapController {

    private final BootstrapService bootstrapService;

    public BootstrapController(BootstrapService bootstrapService) {
        this.bootstrapService = bootstrapService;
    }

    @GetMapping("/bootstrap")
    public ApiResponse<BootstrapData> bootstrap() {
        return ApiResponse.ok(bootstrapService.load());
    }
}
