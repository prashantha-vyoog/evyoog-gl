package com.evyoog.gl.auth.service;

import com.evyoog.gl.auth.domain.User;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class JwtServiceTest {

    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService();
        ReflectionTestUtils.setField(jwtService, "jwtSecret", "evyoog-jwt-secret-key-minimum-256-bits-long-replace-in-production");
        ReflectionTestUtils.setField(jwtService, "jwtExpirationMs", 3600000L);
    }

    private User user() {
        return User.builder()
                .id(UUID.randomUUID())
                .email("prashanth@evyoog.com")
                .fullName("Prashanth")
                .build();
    }

    @Test
    void testGenerateToken_containsPermissions() {
        User user = user();
        UUID legalEntityId = UUID.randomUUID();
        Set<String> permissions = Set.of("gl:journal:view", "gl:journal:approve");

        String token = jwtService.generateAccessToken(user, legalEntityId, permissions);
        Claims claims = jwtService.parseToken(token);

        @SuppressWarnings("unchecked")
        var claimedPermissions = (java.util.Collection<String>) claims.get("permissions", java.util.Collection.class);
        assertThat(claimedPermissions).containsExactlyInAnyOrderElementsOf(permissions);
    }

    @Test
    void testParseToken_validToken_returnsCorrectClaims() {
        User user = user();
        UUID legalEntityId = UUID.randomUUID();

        String token = jwtService.generateAccessToken(user, legalEntityId, Set.of("gl:journal:view"));
        Claims claims = jwtService.parseToken(token);

        assertThat(claims.getSubject()).isEqualTo(user.getId().toString());
        assertThat(claims.get("email")).isEqualTo(user.getEmail());
        assertThat(claims.get("legalEntityId")).isEqualTo(legalEntityId.toString());
    }

    @Test
    void testIsTokenValid_expiredToken_returnsFalse() {
        ReflectionTestUtils.setField(jwtService, "jwtExpirationMs", -1000L);
        String expiredToken = jwtService.generateAccessToken(user(), UUID.randomUUID(), Set.of());

        assertThat(jwtService.isTokenValid(expiredToken)).isFalse();
    }

    @Test
    void testIsTokenValid_validToken_returnsTrue() {
        String token = jwtService.generateAccessToken(user(), UUID.randomUUID(), Set.of());

        assertThat(jwtService.isTokenValid(token)).isTrue();
    }

    @Test
    void testIsTokenValid_garbageToken_returnsFalse() {
        assertThat(jwtService.isTokenValid("not-a-jwt")).isFalse();
    }
}
