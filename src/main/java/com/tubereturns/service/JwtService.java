package com.tubereturns.service;

import com.tubereturns.model.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

@Service
public class JwtService {

    private static final String TOKEN_VERSION_CLAIM = "tokenVersion";
    private static final String SESSION_START_CLAIM = "sessionStart";

    @Value("${tubereturns.security.jwt-secret}")
    private String jwtSecret;

    @Value("${tubereturns.security.jwt-expiration-ms}")
    private long jwtExpirationMs;

    @Value("${tubereturns.security.session-max-age-ms}")
    private long sessionMaxAgeMs;

    public String generateToken(User user) {
        return generateToken(user, Instant.now());
    }

    public String renewToken(User user, String currentToken) {
        return generateToken(user, sessionStart(extractClaims(currentToken)));
    }

    public String generateToken(User user, Instant sessionStart) {
        var builder = Jwts.builder()
                .subject(user.getEmail())
                .claim("firstName", user.getFirstName())
                .claim("role", user.getRole().getName())
                .claim(TOKEN_VERSION_CLAIM, user.getTokenVersion())
                .claim(SESSION_START_CLAIM, sessionStart.getEpochSecond())
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + jwtExpirationMs));
        if (user.getLastName() != null) {
            builder.claim("lastName", user.getLastName());
        }
        if (user.getProfilePictureUrl() != null) {
            builder.claim("picture", user.getProfilePictureUrl());
        }
        builder.claim("notifyOnChannelProcessed", user.isNotifyOnChannelProcessed());
        return builder.signWith(signingKey()).compact();
    }

    public String extractEmail(String token) {
        return extractClaims(token).getSubject();
    }

    public boolean isTokenValid(String token, User user) {
        try {
            Claims claims = extractClaims(token);
            return claims.getSubject().equals(user.getUsername())
                    && !claims.getExpiration().before(new Date())
                    && tokenVersion(claims) == user.getTokenVersion()
                    && sessionStart(claims).plusMillis(sessionMaxAgeMs).isAfter(Instant.now());
        } catch (Exception e) {
            return false;
        }
    }

    private int tokenVersion(Claims claims) {
        Integer version = claims.get(TOKEN_VERSION_CLAIM, Integer.class);
        return version != null ? version : 0;
    }

    private Instant sessionStart(Claims claims) {
        Long epochSeconds = claims.get(SESSION_START_CLAIM, Long.class);
        return epochSeconds != null ? Instant.ofEpochSecond(epochSeconds) : claims.getIssuedAt().toInstant();
    }

    private Claims extractClaims(String token) {
        return Jwts.parser()
                .verifyWith(signingKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private SecretKey signingKey() {
        return Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
    }
}
