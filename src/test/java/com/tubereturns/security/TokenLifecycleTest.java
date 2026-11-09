package com.tubereturns.security;

import com.jayway.jsonpath.JsonPath;
import com.tubereturns.model.User;
import com.tubereturns.service.UserService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TokenLifecycleTest extends ApiIntegrationTest {

    @Autowired
    UserService userService;

    @Value("${tubereturns.security.jwt-secret}")
    String jwtSecret;

    @Test
    void passwordResetInvalidatesTokensIssuedBeforeIt() throws Exception {
        User user = userWithRole("reset-victim@example.test", "FREE");
        String stolenToken = jwtService.generateToken(user);
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + stolenToken)).andExpect(status().isOk());

        userService.initiatePasswordReset(user.getEmail());
        String resetToken = userRepository.findByEmail(user.getEmail()).orElseThrow().getPasswordResetToken();
        userService.resetPassword(resetToken, "An0ther!Passw0rd");

        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + stolenToken)).andExpect(status().isUnauthorized());

        String freshToken = jwtService.generateToken(userRepository.findByEmail(user.getEmail()).orElseThrow());
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + freshToken)).andExpect(status().isOk());
    }

    @Test
    void renewingATokenDoesNotExtendTheSession() throws Exception {
        User user = userWithRole("renewer@example.test", "FREE");
        Instant sessionStart = Instant.now().minus(Duration.ofDays(29)).truncatedTo(ChronoUnit.SECONDS);
        String token = jwtService.generateToken(user, sessionStart);

        String body = mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String renewed = JsonPath.read(body, "$.token");

        assertThat(claimsOf(renewed).get("sessionStart", Long.class)).isEqualTo(sessionStart.getEpochSecond());
    }

    @Test
    void aSessionOlderThanTheMaximumAgeIsRejected() throws Exception {
        User user = userWithRole("old-session@example.test", "FREE");
        String token = jwtService.generateToken(user, Instant.now().minus(Duration.ofDays(31)));

        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token)).andExpect(status().isUnauthorized());
    }

    @Test
    void tokensIssuedBeforeVersioningKeepWorkingUntilAReset() throws Exception {
        User user = userWithRole("legacy-token@example.test", "FREE");
        String legacyToken = Jwts.builder()
                .subject(user.getEmail())
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(signingKey())
                .compact();

        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + legacyToken)).andExpect(status().isOk());
    }

    private Claims claimsOf(String token) {
        return Jwts.parser().verifyWith(signingKey()).build().parseSignedClaims(token).getPayload();
    }

    private SecretKey signingKey() {
        return Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
    }
}
