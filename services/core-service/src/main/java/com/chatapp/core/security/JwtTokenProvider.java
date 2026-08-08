package com.chatapp.core.security;

import java.text.ParseException;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.chatapp.core.base.config.JwtProperties;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Signs the access_token (JWT RS256). Payload contains ONLY sub/jti/iat/exp — no business
 * claims (role, display_name...) that could go stale before the token expires.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class JwtTokenProvider {

    private final JwtKeyManager keyManager;
    private final JwtProperties properties;

    /** Returns the jti alongside the serialized token — callers that persist a session
     *  (AuthServiceImpl#buildAuthResult) need it to enable immediate single-device revocation
     *  later (logoutSession); it's otherwise thrown away once minted since it's already
     *  embedded in the returned token itself. */
    public IssuedAccessToken generateAccessToken(UUID userId) {
        try {
            Instant now = Instant.now();
            Instant expiresAt = now.plusSeconds(properties.getAccessTokenTtlSeconds());
            String jti = UUID.randomUUID().toString();

            JWTClaimsSet claims = new JWTClaimsSet.Builder()
                    .subject(userId.toString())
                    .jwtID(jti)
                    .issueTime(Date.from(now))
                    .expirationTime(Date.from(expiresAt))
                    .build();

            JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.RS256)
                    .keyID(keyManager.getKeyId())
                    .build();

            SignedJWT signedJwt = new SignedJWT(header, claims);
            signedJwt.sign(new RSASSASigner(keyManager.getSigningKey()));

            return new IssuedAccessToken(signedJwt.serialize(), jti);
        } catch (JOSEException e) {
            throw new IllegalStateException("Failed to sign access token", e);
        }
    }

    public long getAccessTokenTtlSeconds() {
        return properties.getAccessTokenTtlSeconds();
    }

    /** sub/jti/exp pulled from an access_token this instance already signed — used by logout to
     *  know which jti to blacklist. Signature is re-verified locally (public key is already in
     *  memory, no network round trip) rather than trusted blindly, even though the caller only
     *  reaches here via a request the Gateway already authenticated. */
    public Optional<AccessTokenClaims> parseAndVerify(String token) {
        try {
            SignedJWT signedJwt = SignedJWT.parse(token);
            if (!signedJwt.verify(new RSASSAVerifier(keyManager.getSigningKey().toRSAPublicKey()))) {
                return Optional.empty();
            }
            JWTClaimsSet claims = signedJwt.getJWTClaimsSet();
            return Optional.of(new AccessTokenClaims(
                    UUID.fromString(claims.getSubject()),
                    claims.getJWTID(),
                    claims.getExpirationTime().toInstant()));
        } catch (ParseException | JOSEException | IllegalArgumentException | NullPointerException e) {
            log.warn("Rejected invalid access_token during parse/verify: {}", e.getMessage());
            return Optional.empty();
        }
    }

    public record AccessTokenClaims(UUID userId, String jti, Instant expiresAt) {
    }

    public record IssuedAccessToken(String token, String jti) {
    }
}
