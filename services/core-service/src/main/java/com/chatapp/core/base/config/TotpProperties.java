package com.chatapp.core.base.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@ConfigurationProperties(prefix = "app.totp")
public class TotpProperties {

    /** Base64-encoded AES-256 key. Blank in dev auto-generates one in memory at startup
     *  (see TotpSecretCipher) — production MUST set this via env, or every restart makes
     *  existing totp_secret_enc rows undecryptable. */
    private String secretEncryptionKey = "";

    /** Shown as the account/issuer name inside the user's authenticator app (Google
     *  Authenticator, Authy...) — part of the otpauth:// URI, not hardcoded so white-labeling
     *  or renaming the product doesn't need a code change. */
    private String issuer = "ChatApp";
}
