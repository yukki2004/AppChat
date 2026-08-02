package com.chatapp.core.base.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@ConfigurationProperties(prefix = "app.otp")
public class OtpProperties {

    private String mailFrom = "no-reply@chatapp.com";
    private long codeTtlSeconds = 300;
    private int maxAttempts = 5;

    /** Cooldown after exhausting maxAttempts — during this window even requesting a brand new
     *  code (OtpCodeService.generate) is blocked, not just verifying the old one. Without this,
     *  a locked-out code is meaningless: the caller can just request a fresh one immediately
     *  and get 5 more tries, forever. */
    private long lockoutSeconds = 1800;
}
