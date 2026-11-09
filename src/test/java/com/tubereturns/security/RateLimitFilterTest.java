package com.tubereturns.security;

import com.tubereturns.config.RateLimitProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimitFilterTest {

    private static final int AUTH_CAPACITY = 10;

    private RateLimitFilter filter;

    @BeforeEach
    void setUp() {
        RateLimitProperties props = new RateLimitProperties();
        props.setEnabled(true);
        props.setAuth(tier(AUTH_CAPACITY));
        props.setStandard(tier(50));
        props.setAuthenticated(tier(100));
        filter = new RateLimitFilter(props, JsonMapper.builder().build());
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void rotatingSpoofableHeadersDoesNotGrantFreshBuckets() throws Exception {
        int lastStatus = 200;
        for (int i = 0; i <= AUTH_CAPACITY; i++) {
            MockHttpServletRequest request = loginFrom("203.0.113.7");
            request.addHeader("CF-Connecting-IP", "198.51.100." + i);
            request.addHeader("X-Forwarded-For", "198.51.100." + i);
            lastStatus = statusOf(request);
        }
        assertThat(lastStatus).isEqualTo(429);
    }

    @Test
    void anAuthenticatedCallerStaysInTheStrictTierOnCredentialEndpoints() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("attacker@example.test", null, List.of()));
        int lastStatus = 200;
        for (int i = 0; i <= AUTH_CAPACITY; i++) {
            lastStatus = statusOf(loginFrom("203.0.113.8"));
        }
        assertThat(lastStatus).isEqualTo(429);
    }

    @Test
    void clientsAreSeparatedByTheProxySuppliedAddress() throws Exception {
        for (int i = 0; i <= AUTH_CAPACITY; i++) {
            MockHttpServletRequest request = loginFrom("172.18.0.5");
            request.addHeader("X-Real-IP", "203.0.113.20");
            statusOf(request);
        }
        MockHttpServletRequest otherClient = loginFrom("172.18.0.5");
        otherClient.addHeader("X-Real-IP", "203.0.113.21");
        assertThat(statusOf(otherClient)).isEqualTo(200);
    }

    private MockHttpServletRequest loginFrom(String remoteAddr) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/login");
        request.setRemoteAddr(remoteAddr);
        return request;
    }

    private int statusOf(MockHttpServletRequest request) throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        return response.getStatus();
    }

    private RateLimitProperties.Tier tier(int capacity) {
        RateLimitProperties.Tier tier = new RateLimitProperties.Tier();
        tier.setCapacity(capacity);
        tier.setRefillSeconds(60);
        return tier;
    }
}
