package com.tubereturns.security;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.json.webtoken.JsonWebSignature;
import com.tubereturns.model.User;
import com.tubereturns.service.UserService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SignupTakeoverTest extends ApiIntegrationTest {

    private static final String ATTACKER_PASSWORD = "Attacker!Passw0rd1";
    private static final String OWNER_PASSWORD = "Owner!Passw0rd22";

    @Autowired
    UserService userService;

    private final GoogleIdTokenVerifier googleVerifier = mock(GoogleIdTokenVerifier.class);
    private Object originalVerifier;

    @BeforeEach
    void replaceGoogleVerifier() {
        originalVerifier = ReflectionTestUtils.getField(userService, "googleVerifier");
        ReflectionTestUtils.setField(userService, "googleVerifier", googleVerifier);
    }

    @AfterEach
    void restoreGoogleVerifier() {
        ReflectionTestUtils.setField(userService, "googleVerifier", originalVerifier);
    }

    @Test
    void theRealOwnerCanSignUpOverASquattedUnverifiedAccount() throws Exception {
        String email = "squatted-signup@example.test";
        mvc.perform(signup(email, ATTACKER_PASSWORD)).andExpect(status().isOk());
        String attackerVerificationToken = userRepository.findByEmail(email).orElseThrow().getVerificationToken();

        mvc.perform(signup(email, OWNER_PASSWORD)).andExpect(status().isOk());

        User account = userRepository.findByEmail(email).orElseThrow();
        assertThat(passwordEncoder.matches(OWNER_PASSWORD, account.getPasswordHash())).isTrue();
        assertThat(passwordEncoder.matches(ATTACKER_PASSWORD, account.getPasswordHash())).isFalse();
        assertThat(account.getVerificationToken()).isNotEqualTo(attackerVerificationToken);
    }

    @Test
    void googleSignInTakesOverASquattedUnverifiedAccount() throws Exception {
        String email = "squatted-google@example.test";
        mvc.perform(signup(email, ATTACKER_PASSWORD)).andExpect(status().isOk());
        when(googleVerifier.verify("owner-credential")).thenReturn(googleToken(email, true));

        mvc.perform(google("owner-credential")).andExpect(status().isOk());

        User account = userRepository.findByEmail(email).orElseThrow();
        assertThat(account.isEmailVerified()).isTrue();
        assertThat(account.getProvider()).isEqualTo("google");
        assertThat(account.getPasswordHash()).isNull();
        mvc.perform(login(email, ATTACKER_PASSWORD)).andExpect(status().isUnauthorized());
    }

    @Test
    void passwordResetUnlocksASquattedUnverifiedAccount() throws Exception {
        String email = "squatted-reset@example.test";
        mvc.perform(signup(email, ATTACKER_PASSWORD)).andExpect(status().isOk());
        mvc.perform(login(email, ATTACKER_PASSWORD)).andExpect(status().isForbidden());

        userService.initiatePasswordReset(email);
        String resetToken = userRepository.findByEmail(email).orElseThrow().getPasswordResetToken();
        userService.resetPassword(resetToken, OWNER_PASSWORD);

        mvc.perform(login(email, OWNER_PASSWORD)).andExpect(status().isOk());
        mvc.perform(login(email, ATTACKER_PASSWORD)).andExpect(status().isUnauthorized());
    }

    @Test
    void aVerifiedAccountCannotBeReplacedBySignup() throws Exception {
        User verified = userWithRole("verified-owner@example.test", "FREE");

        mvc.perform(signup(verified.getEmail(), ATTACKER_PASSWORD)).andExpect(status().isBadRequest());

        User account = userRepository.findByEmail(verified.getEmail()).orElseThrow();
        assertThat(passwordEncoder.matches(PASSWORD, account.getPasswordHash())).isTrue();
        assertThat(account.isEmailVerified()).isTrue();
    }

    @Test
    void googleTokenWithUnverifiedEmailIsRejected() throws Exception {
        String email = "unverified-google@example.test";
        when(googleVerifier.verify("unverified-credential")).thenReturn(googleToken(email, false));

        mvc.perform(google("unverified-credential")).andExpect(status().isBadRequest());

        assertThat(userRepository.findByEmail(email)).isEmpty();
    }

    private GoogleIdToken googleToken(String email, boolean emailVerified) {
        GoogleIdToken.Payload payload = new GoogleIdToken.Payload();
        payload.setEmail(email);
        payload.setEmailVerified(emailVerified);
        payload.setSubject("google-subject-" + email);
        payload.set("given_name", "Owner");
        return new GoogleIdToken(new JsonWebSignature.Header(), payload, new byte[0], new byte[0]);
    }

    private MockHttpServletRequestBuilder signup(String email, String password) {
        return json("/api/auth/signup", "{\"firstName\":\"Someone\",\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, password));
    }

    private MockHttpServletRequestBuilder login(String email, String password) {
        return json("/api/auth/login", "{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, password));
    }

    private MockHttpServletRequestBuilder google(String credential) {
        return json("/api/auth/google", "{\"credential\":\"%s\"}".formatted(credential));
    }

    private MockHttpServletRequestBuilder json(String path, String body) {
        return post(path).contentType(MediaType.APPLICATION_JSON).content(body);
    }
}
