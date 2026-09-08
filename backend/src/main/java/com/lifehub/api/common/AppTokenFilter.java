package com.lifehub.api.common;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lifehub.domain.common.IdGenerator;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.cors.CorsUtils;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Rejects any {@code /api/v1} request that does not present the shared startup token (NFR-SEC-04).
 *
 * <p>Electron generates a fresh random token every launch, passes it to the backend as a command
 * line argument and to the renderer over IPC. Combined with binding to 127.0.0.1 (NFR-SEC-02) this
 * stops another local process from talking to the backend.
 *
 * <p>{@code /actuator/health} is deliberately outside the guarded prefix: Electron polls it during
 * startup as a pure liveness probe, and it exposes no user data.
 *
 * <p>This filter runs before Spring MVC, so {@link GlobalExceptionHandler} cannot see its failures.
 * It therefore writes the same error envelope itself rather than throwing.
 */
@Component
public class AppTokenFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(AppTokenFilter.class);

    static final String HEADER = "X-App-Token";
    static final String GUARDED_PREFIX = "/api/v1";

    private final String expectedToken;
    private final ObjectMapper objectMapper;

    public AppTokenFilter(AppProperties properties, ObjectMapper objectMapper) {
        this.expectedToken = properties.token();
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (!request.getRequestURI().startsWith(GUARDED_PREFIX)) {
            return true;
        }
        // A CORS preflight carries no custom headers by specification, so it cannot present the
        // token. Rejecting it here would fail the request before the real, authenticated one is
        // ever sent. Spring's CORS handler answers the preflight, which exposes no user data.
        return CorsUtils.isPreFlightRequest(request);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        String presented = request.getHeader(HEADER);
        if (!matches(presented)) {
            String traceId = IdGenerator.newId();
            log.warn("Rejected {} {} - {} header {} [traceId={}]",
                    request.getMethod(), request.getRequestURI(), HEADER,
                    presented == null ? "missing" : "invalid", traceId);
            writeUnauthorized(response, traceId);
            return;
        }
        chain.doFilter(request, response);
    }

    /** Constant time comparison so a wrong token cannot be guessed byte by byte from timing. */
    private boolean matches(String presented) {
        if (presented == null) {
            return false;
        }
        return MessageDigest.isEqual(
                presented.getBytes(StandardCharsets.UTF_8), expectedToken.getBytes(StandardCharsets.UTF_8));
    }

    private void writeUnauthorized(HttpServletResponse response, String traceId) throws IOException {
        ApiError error = ApiError.of(ErrorCode.UNAUTHORIZED,
                "Phiên làm việc không hợp lệ. Hãy khởi động lại ứng dụng.", traceId);
        response.setStatus(ErrorCode.UNAUTHORIZED.getStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getOutputStream(), ApiResponse.fail(error));
    }
}
