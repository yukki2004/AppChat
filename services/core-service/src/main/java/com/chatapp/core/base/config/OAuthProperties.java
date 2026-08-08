package com.chatapp.core.base.config;

import java.util.HashMap;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Getter;
import lombok.Setter;

/** Keyed by lowercase provider name (`app.oauth.providers.google.client-id=...`) — adding
 *  Facebook/Apple later is a config-only change (new map entry + new env vars), no new Java
 *  class needed here. {@link com.chatapp.core.auth.oauth.strategy.OAuthProviderStrategy}
 *  implementations look up their own entry by {@code OAuthProvider.name().toLowerCase()}. */
@Getter
@Setter
@ConfigurationProperties(prefix = "app.oauth")
public class OAuthProperties {

    private Map<String, Provider> providers = new HashMap<>();

    public Provider get(String providerName) {
        Provider provider = providers.get(providerName);
        if (provider == null) {
            throw new IllegalStateException("No OAuth config for provider: " + providerName);
        }
        return provider;
    }

    @Getter
    @Setter
    public static class Provider {
        private String clientId = "";
        private String clientSecret = "";
        private String redirectUri = "";
    }
}
