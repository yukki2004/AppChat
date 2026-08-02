package com.chatapp.core.base.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@ConfigurationProperties(prefix = "jwt")
public class JwtProperties {

    private String privateKeyPath = "./keys/private_key.pem";
    private String publicKeyPath = "./keys/public_key.pem";
    private String keyId = "core-dev-key-1";
    private long accessTokenTtlSeconds = 900;
    private long refreshTokenTtlSeconds = 2_592_000;
}
