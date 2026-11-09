package com.tubereturns.security;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@TestPropertySource(properties = {
        "tubereturns.rate-limit.enabled=true",
        "tubereturns.rate-limit.auth.capacity=1000",
        "tubereturns.rate-limit.login-account.capacity=5"
})
class LoginThrottleTest extends ApiIntegrationTest {

    @Test
    void repeatedFailuresLockTheAccountEvenForTheCorrectPassword() throws Exception {
        userWithRole("throttled@example.test", "FREE");
        userWithRole("bystander@example.test", "FREE");

        for (int i = 0; i < 5; i++) {
            mvc.perform(login("throttled@example.test", "wrong-password-" + i))
                    .andExpect(status().isUnauthorized());
        }

        mvc.perform(login("Throttled@Example.test", PASSWORD)).andExpect(status().isTooManyRequests());
        mvc.perform(login("bystander@example.test", PASSWORD)).andExpect(status().isOk());
    }

    private MockHttpServletRequestBuilder login(String email, String password) {
        return post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, password));
    }
}
