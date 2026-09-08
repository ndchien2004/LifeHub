package com.lifehub.api.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/** Unit coverage for the shared token guard (NFR-SEC-04). */
class AppTokenFilterTest {

    private static final String TOKEN = "correct-horse-battery-staple";

    private final AppTokenFilter filter =
            new AppTokenFilter(new AppProperties(TOKEN, "data", "logs"), new ObjectMapper());

    @Test
    @DisplayName("Token đúng thì request đi tiếp")
    void passesRequestWithCorrectToken() throws Exception {
        MockHttpServletRequest request = apiRequest();
        request.addHeader("X-App-Token", TOKEN);
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("Thiếu token thì chặn với 401 và không gọi tiếp chain")
    void blocksRequestWithoutToken() throws Exception {
        MockHttpServletRequest request = apiRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        verify(chain, never()).doFilter(request, response);
        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("\"UNAUTHORIZED\"");
    }

    @Test
    @DisplayName("Token sai thì chặn với 401")
    void blocksRequestWithWrongToken() throws Exception {
        MockHttpServletRequest request = apiRequest();
        request.addHeader("X-App-Token", TOKEN + "x");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        verify(chain, never()).doFilter(request, response);
        assertThat(response.getStatus()).isEqualTo(401);
    }

    @Test
    @DisplayName("Đường dẫn ngoài /api/v1 không bị filter đụng tới")
    void ignoresPathsOutsideTheApiPrefix() {
        MockHttpServletRequest health = new MockHttpServletRequest("GET", "/actuator/health");

        assertThat(filter.shouldNotFilter(health)).isTrue();
        assertThat(filter.shouldNotFilter(apiRequest())).isFalse();
    }

    @Test
    @DisplayName("Không cấu hình token thì tự sinh, không bao giờ chạy với token rỗng")
    void neverRunsWithAnEmptyToken() {
        AppProperties generated = new AppProperties("  ", null, null);

        assertThat(generated.token()).isNotBlank().hasSizeGreaterThan(20);
        assertThat(generated.dataDir()).isEqualTo("data");
        assertThat(generated.logDir()).isEqualTo("logs");
    }

    private MockHttpServletRequest apiRequest() {
        return new MockHttpServletRequest("GET", "/api/v1/bootstrap");
    }
}
