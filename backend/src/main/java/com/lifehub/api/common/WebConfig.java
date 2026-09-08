package com.lifehub.api.common;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * CORS policy for the renderer.
 *
 * <p>The renderer and the backend always sit on different ports, so every call is cross origin
 * and the {@code X-App-Token} header makes it a non-simple request - the browser sends an
 * {@code OPTIONS} preflight first. In development the page is served by Vite on port 5173; in a
 * packaged build it is loaded from disk, and a {@code file://} page sends {@code Origin: null}.
 *
 * <p>This does not widen the attack surface. The preflight response reveals only the policy
 * itself, the actual request still has to present the shared token (NFR-SEC-04), and the server
 * only listens on the loopback interface (NFR-SEC-02).
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private static final String[] ALLOWED_ORIGINS = {
        "http://127.0.0.1:5173",
        "http://localhost:5173",
        // A page loaded from file:// in the packaged app.
        "null",
    };

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/v1/**")
                .allowedOrigins(ALLOWED_ORIGINS)
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders("Content-Type", AppTokenFilter.HEADER)
                // No cookies or HTTP auth are involved; the shared token is the only credential.
                .allowCredentials(false)
                .maxAge(3600);
    }
}
