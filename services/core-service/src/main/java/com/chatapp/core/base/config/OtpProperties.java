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

    /** Minimum gap between 2 successful generate() calls for the same (target, purpose) —
     *  separate from lockoutSeconds above (that one only triggers after wrong verify attempts).
     *  Without this, an endpoint like POST /auth/register/otp can be called repeatedly with
     *  someone else's real email/phone to spam them with mail/SMS, at no cost to the caller and
     *  real cost (SMS provider fees, an inbox full of codes) to the victim. */
    private long resendCooldownSeconds = 60;
}
