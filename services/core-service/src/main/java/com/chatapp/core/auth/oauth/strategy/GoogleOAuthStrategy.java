package com.chatapp.core.auth.oauth.strategy;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.text.ParseException;
import java.util.Map;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.chatapp.core.auth.oauth.dto.OAuthUserInfo;
import com.chatapp.core.base.config.OAuthProperties;
import com.chatapp.core.base.constant.OAuthProvider;
import com.chatapp.core.exception.common.AppException;
import com.chatapp.core.exception.common.ErrorCode;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.jwk.source.JWKSourceBuilder;
import com.nimbusds.jose.proc.BadJOSEException;
import com.nimbusds.jose.proc.JWSKeySelector;
import com.nimbusds.jose.proc.JWSVerificationKeySelector;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.proc.ConfigurableJWTProcessor;
import com.nimbusds.jwt.proc.DefaultJWTProcessor;

import lombok.extern.slf4j.Slf4j;


@Component
@Slf4j
public class GoogleOAuthStrategy implements OAuthProviderStrategy {

    private static final String CONFIG_KEY = "google";
    private static final String TOKEN_ENDPOINT = "https://oauth2.googleapis.com/token";
    private static final String JWKS_URI = "https://www.googleapis.com/oauth2/v3/certs";

    private final OAuthProperties properties;
    private final RestClient restClient = RestClient.create();
    private final ConfigurableJWTProcessor<SecurityContext> jwtProcessor = buildJwtProcessor();

    public GoogleOAuthStrategy(OAuthProperties properties) {
        this.properties = properties;
    }

    @Override
    public OAuthProvider provider() {
        return OAuthProvider.GOOGLE;
    }

    @Override
    public OAuthUserInfo exchangeCode(String code) {
        OAuthProperties.Provider config = properties.get(CONFIG_KEY);
        log.debug("GoogleOAuthStrategy exchangeCode start");

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("code", code);
        form.add("client_id", config.getClientId());
        form.add("client_secret", config.getClientSecret());
        form.add("redirect_uri", config.getRedirectUri());
        form.add("grant_type", "authorization_code");

        Map<String, Object> tokenResponse;
        try {
            tokenResponse = restClient.post()
                    .uri(TOKEN_ENDPOINT)
                    .body(form)
                    .retrieve()
                    .body(new ParameterizedTypeReference<Map<String, Object>>() {
                    });
        } catch (RestClientException e) {
            log.warn("GoogleOAuthStrategy code exchange rejected by Google: {}", e.getMessage());
            throw new AppException(ErrorCode.OAUTH_CODE_EXCHANGE_FAILED);
        }

        String idToken = tokenResponse == null ? null : (String) tokenResponse.get("id_token");
        if (idToken == null) {
            log.warn("GoogleOAuthStrategy token response missing id_token");
            throw new AppException(ErrorCode.OAUTH_CODE_EXCHANGE_FAILED);
        }

        JWTClaimsSet claims = verifyIdToken(idToken, config.getClientId());
        boolean emailVerified = Boolean.TRUE.equals(claims.getClaim("email_verified"));

        try {
            OAuthUserInfo userInfo = new OAuthUserInfo(
                    claims.getSubject(), claims.getStringClaim("email"), emailVerified, claims.getStringClaim("name"));
            log.info("GoogleOAuthStrategy exchangeCode success providerUserId={} emailVerified={}",
                    userInfo.providerUserId(), emailVerified);
            return userInfo;
        } catch (ParseException e) {
            log.warn("GoogleOAuthStrategy id_token claims malformed: {}", e.getMessage());
            throw new AppException(ErrorCode.OAUTH_CODE_EXCHANGE_FAILED);
        }
    }


    private JWTClaimsSet verifyIdToken(String idToken, String expectedAudience) {
        try {
            JWTClaimsSet claims = jwtProcessor.process(idToken, null);
            if (claims.getAudience() == null || !claims.getAudience().contains(expectedAudience)) {
                log.warn("GoogleOAuthStrategy id_token audience mismatch — rejecting");
                throw new AppException(ErrorCode.OAUTH_CODE_EXCHANGE_FAILED);
            }
            return claims;
        } catch (ParseException | JOSEException | BadJOSEException e) {
            log.warn("GoogleOAuthStrategy id_token verification failed: {}", e.getMessage());
            throw new AppException(ErrorCode.OAUTH_CODE_EXCHANGE_FAILED);
        }
    }

    private static ConfigurableJWTProcessor<SecurityContext> buildJwtProcessor() {
        try {
            JWKSource<SecurityContext> jwkSource = JWKSourceBuilder.<SecurityContext>create(new URI(JWKS_URI).toURL()).build();
            ConfigurableJWTProcessor<SecurityContext> processor = new DefaultJWTProcessor<>();
            JWSKeySelector<SecurityContext> keySelector =
                    new JWSVerificationKeySelector<>(JWSAlgorithm.RS256, jwkSource);
            processor.setJWSKeySelector(keySelector);
            return processor;
        } catch (URISyntaxException | MalformedURLException e) {
            throw new IllegalStateException("Invalid Google JWKS URI", e);
        }
    }
}
