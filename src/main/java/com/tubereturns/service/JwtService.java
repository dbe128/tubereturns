package com.tubereturns.service;

import com.tubereturns.model.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Service
public class JwtService {

    @Value("${tubereturns.security.jwt-secret}")
    private String jwtSecret;

    @Value("${tubereturns.security.jwt-expiration-ms}")
    private long jwtExpirationMs;

    public String generateToken(User user) {
        var builder = Jwts.builder()
                .subject(user.getEmail())
                .claim("firstName", user.getFirstName())
                .claim("role", user.getRole().getName())
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

    public boolean isTokenValid(String token, UserDetails userDetails) {
        try {
            String email = extractEmail(token);
            return email.equals(userDetails.getUsername()) && !isTokenExpired(token);
        } catch (Exception e) {
            return false;
        }
    }

    private boolean isTokenExpired(String token) {
        return extractClaims(token).getExpiration().before(new Date());
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
