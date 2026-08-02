package com.chatapp.core.security;

import java.time.Instant;
import java.util.Date;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.chatapp.core.base.config.JwtProperties;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

import lombok.RequiredArgsConstructor;

/**
 * Signs the access_token (JWT RS256). Payload contains ONLY sub/jti/iat/exp — no business
 * claims (role, display_name...) that could go stale before the token expires.
 */
@Component
@RequiredArgsConstructor
public class JwtTokenProvider {

    private final JwtKeyManager keyManager;
    private final JwtProperties properties;

    public String generateAccessToken(UUID userId) {
        try {
            Instant now = Instant.now();
            Instant expiresAt = now.plusSeconds(properties.getAccessTokenTtlSeconds());

            JWTClaimsSet claims = new JWTClaimsSet.Builder()
                    .subject(userId.toString())
                    .jwtID(UUID.randomUUID().toString())
                    .issueTime(Date.from(now))
                    .expirationTime(Date.from(expiresAt))
                    .build();

            JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.RS256)
                    .keyID(keyManager.getKeyId())
                    .build();

            SignedJWT signedJwt = new SignedJWT(header, claims);
            signedJwt.sign(new RSASSASigner(keyManager.getSigningKey()));

            return signedJwt.serialize();
        } catch (JOSEException e) {
            throw new IllegalStateException("Failed to sign access token", e);
        }
    }

    public long getAccessTokenTtlSeconds() {
        return properties.getAccessTokenTtlSeconds();
    }
}
