package com.chatapp.core.auth.oauth.service;

import java.security.SecureRandom;
import java.util.Locale;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.chatapp.core.auth.AuthService;
import com.chatapp.core.auth.oauth.OAuthService;
import com.chatapp.core.auth.oauth.dto.OAuthUserInfo;
import com.chatapp.core.auth.oauth.strategy.OAuthProviderDispatcher;
import com.chatapp.core.auth.result.LoginOutcome;
import com.chatapp.core.base.constant.OAuthProvider;
import com.chatapp.core.base.entity.UserEntity;
import com.chatapp.core.base.entity.UserOAuthProviderEntity;
import com.chatapp.core.base.repository.UserOAuthProviderRepository;
import com.chatapp.core.base.repository.UserRepository;
import com.chatapp.core.exception.common.AppException;
import com.chatapp.core.exception.common.ErrorCode;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;


@Service
@RequiredArgsConstructor
@Slf4j
public class OAuthServiceImpl implements OAuthService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final OAuthProviderDispatcher dispatcher;
    private final UserOAuthProviderRepository userOAuthProviderRepository;
    private final UserRepository userRepository;
    private final AuthService authService;

    @Override
    @Transactional
    public LoginOutcome loginWithCallback(OAuthProvider provider, String code, String ipAddress, String userAgent) {
        log.debug("loginWithCallback start provider={}", provider);
        OAuthUserInfo userInfo = dispatcher.exchangeCode(provider, code);

        Optional<UserOAuthProviderEntity> existingLink =
                userOAuthProviderRepository.findByProviderAndProviderUserId(provider, userInfo.providerUserId());

        UserEntity user = existingLink.isPresent()
                ? userRepository.findByIdAndDeletedAtIsNull(existingLink.get().getUserId())
                        .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND))
                : registerNewUser(provider, userInfo);

        log.info("loginWithCallback success provider={} userId={} newUser={}", provider, user.getId(), existingLink.isEmpty());
        return authService.completeLogin(user, ipAddress, userAgent);
    }

    private UserEntity registerNewUser(OAuthProvider provider, OAuthUserInfo userInfo) {
        if (userInfo.email() != null && userRepository.existsByEmail(userInfo.email())) {
            log.warn("loginWithCallback rejected provider={} reason=EMAIL_ALREADY_REGISTERED", provider);
            throw new AppException(ErrorCode.OAUTH_EMAIL_ALREADY_REGISTERED);
        }

        String displayName = userInfo.displayName() != null ? userInfo.displayName() : userInfo.email();
        UserEntity user = new UserEntity(
                generateUniqueUsername(userInfo.email()), userInfo.email(), null, null, displayName);
        UserEntity saved = userRepository.save(user);

        UserOAuthProviderEntity link = new UserOAuthProviderEntity(
                saved.getId(), provider, userInfo.providerUserId(), userInfo.email());
        userOAuthProviderRepository.save(link);

        log.info("registerNewUser success provider={} userId={}", provider, saved.getId());
        return saved;
    }


    private String generateUniqueUsername(String email) {
        String base = email != null
                ? email.substring(0, email.indexOf('@')).toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "")
                : "user";
        if (base.isBlank()) {
            base = "user";
        }
        String candidate = base;
        while (userRepository.existsByUsername(candidate)) {
            candidate = base + (1000 + RANDOM.nextInt(9000));
        }
        return candidate;
    }
}
