package com.chatapp.core.security;

import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.nimbusds.jose.jwk.JWKSet;

import lombok.RequiredArgsConstructor;

/**
 * Public endpoint, no auth required — API Gateway/Realtime Gateway/other services fetch the
 * public key here to verify JWT signatures locally, without an internal gRPC round trip per
 * request.
 */
@RestController
@RequiredArgsConstructor
public class JwksController {

    private final JwtKeyManager keyManager;

    @GetMapping("/.well-known/jwks.json")
    public Map<String, Object> jwks() {
        return new JWKSet(keyManager.getPublicJwk()).toJSONObject();
    }
}
