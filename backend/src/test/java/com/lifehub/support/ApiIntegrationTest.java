package com.lifehub.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Shared plumbing for API integration tests.
 *
 * <p>Deliberately does not declare the datasource: each concrete class registers its own private
 * database file (see {@link TestDatabase}), which is what keeps test classes from sharing state.
 * Putting it in a static field here would share one database across every subclass instead.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class ApiIntegrationTest {

    protected static final String TOKEN_HEADER = "X-App-Token";
    protected static final String TOKEN = "test-token";

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    /** Attaches the shared token and JSON content type that every real request carries. */
    protected MockHttpServletRequestBuilder authed(MockHttpServletRequestBuilder builder) {
        return builder.header(TOKEN_HEADER, TOKEN).contentType(MediaType.APPLICATION_JSON);
    }

    protected String json(Object body) throws Exception {
        return objectMapper.writeValueAsString(body);
    }

    /** Reads the {@code data} member of the standard response envelope. */
    protected JsonNode data(MvcResult result) throws Exception {
        return objectMapper.readTree(body(result)).path("data");
    }

    /**
     * Response body decoded as UTF-8.
     *
     * <p>MockMvc defaults to ISO-8859-1 when the response carries no charset, which turns every
     * Vietnamese string into mojibake and makes assertions fail for reasons that have nothing to
     * do with the code under test.
     */
    protected String body(MvcResult result) throws Exception {
        return result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
    }
}
