package com.chatapp.core.security;

import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.nimbusds.jose.jwk.JWKSet;

import lombok.RequiredArgsConstructor;

/**
 * Endpoint public, KHÔNG cần auth (đúng RFC 7517 + `skills/authentication.md`) — API Gateway/
 * Realtime Gateway/service khác tự fetch public key ở đây để verify chữ ký JWT tại chỗ, không
 * gọi gRPC ngược lại Core Service mỗi request.
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
