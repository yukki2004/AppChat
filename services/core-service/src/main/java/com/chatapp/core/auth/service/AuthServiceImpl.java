package com.chatapp.core.auth.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.chatapp.core.auth.AuthService;
import com.chatapp.core.auth.dto.request.LoginRequest;
import com.chatapp.core.auth.dto.request.RegisterRequest;
import com.chatapp.core.auth.dto.response.TwoFactorChallengeAckResponse;
import com.chatapp.core.auth.dto.response.UserResponse;
import com.chatapp.core.auth.result.AuthResult;
import com.chatapp.core.auth.result.LoginOutcome;
import com.chatapp.core.auth.result.TwoFactorChallengeResult;
import com.chatapp.core.audit.LoginAuditLogService;
import com.chatapp.core.base.constant.LoginAuditEventType;
import com.chatapp.core.base.constant.TwoFactorMethod;
import com.chatapp.core.base.entity.TwoFactorMethodEntity;
import com.chatapp.core.base.entity.UserEntity;
import com.chatapp.core.base.entity.UserSessionEntity;
import com.chatapp.core.base.repository.TwoFactorMethodRepository;
import com.chatapp.core.base.repository.UserRepository;
import com.chatapp.core.base.repository.UserSessionRepository;
import com.chatapp.core.exception.DuplicateUserException;
import com.chatapp.core.exception.InvalidCredentialsException;
import com.chatapp.core.exception.TwoFactorMethodNotEnabledException;
import com.chatapp.core.geoip.GeoIpService;
import com.chatapp.core.geoip.GeoLookupResult;
import com.chatapp.core.security.JwtTokenProvider;
import com.chatapp.core.twofactor.backupcode.TwoFactorBackupCodeService;
import com.chatapp.core.twofactor.strategy.TwoFactorChallengeDispatcher;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private static final long REFRESH_TOKEN_TTL_SECONDS = 2_592_000; // 30 days, rolling

    /** Pseudo-method, not a {@link TwoFactorMethod} enum value — backup codes are an
     *  account-level fallback (see TwoFactorBackupCodeService), not a row in
     *  `two_factor_methods`, so they can't share that enum without implying a "method" that
     *  doesn't actually exist as its own config. Handled as a special case everywhere the
     *  real methods go through {@link TwoFactorChallengeDispatcher}. */
    private static final String BACKUP_CODE_METHOD = "BACKUP_CODE";

    private final UserRepository userRepository;
    private final UserSessionRepository userSessionRepository;
    private final TwoFactorMethodRepository twoFactorMethodRepository;
    private final TwoFactorChallengeDispatcher twoFactorChallengeDispatcher;
    private final TwoFactorBackupCodeService twoFactorBackupCodeService;
    private final PreAuthTokenService preAuthTokenService;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final GeoIpService geoIpService;
    private final LoginAuditLogService loginAuditLogService;

    @Override
    @Transactional
    public UserResponse register(RegisterRequest request) {
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new DuplicateUserException("Username already exists");
        }
        if (request.getEmail() != null && userRepository.existsByEmail(request.getEmail())) {
            throw new DuplicateUserException("Email is already registered");
        }
        if (request.getPhone() != null && userRepository.existsByPhone(request.getPhone())) {
            throw new DuplicateUserException("Phone number is already registered");
        }

        String passwordHash = passwordEncoder.encode(request.getPassword());
        UserEntity user = new UserEntity(
                request.getUsername(), request.getEmail(), request.getPhone(), passwordHash, request.getDisplayName());
        UserEntity saved = userRepository.save(user);

        return UserResponse.from(saved);
    }

    @Override
    @Transactional
    public LoginOutcome login(LoginRequest request, String ipAddress, String userAgent) {
        Optional<UserEntity> foundUser = findByIdentifier(request.getIdentifier());
        if (foundUser.isEmpty()) {
            loginAuditLogService.record(null, LoginAuditEventType.LOGIN_FAILED, ipAddress, userAgent,
                    null, null, "UNKNOWN_IDENTIFIER");
            throw new InvalidCredentialsException("Invalid username/email/phone or password");
        }
        UserEntity user = foundUser.get();

        if (user.getPasswordHash() == null || !passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            loginAuditLogService.record(user.getId(), LoginAuditEventType.LOGIN_FAILED, ipAddress, userAgent,
                    null, null, "WRONG_PASSWORD");
            throw new InvalidCredentialsException("Invalid username/email/phone or password");
        }
        if (user.isBlocked() || !user.isActive()) {
            loginAuditLogService.record(user.getId(), LoginAuditEventType.LOGIN_FAILED, ipAddress, userAgent,
                    null, null, "ACCOUNT_BLOCKED");
            throw new InvalidCredentialsException("Account is blocked or deactivated");
        }

        List<TwoFactorMethodEntity> twoFactorMethods = twoFactorMethodRepository.findByUserId(user.getId());
        if (!twoFactorMethods.isEmpty()) {
            String preAuthToken = preAuthTokenService.create(user.getId());
            List<String> availableMethods = new ArrayList<>(twoFactorChallengeDispatcher.sortByStrength(twoFactorMethods));
            if (twoFactorBackupCodeService.hasBackupCodes(user.getId())) {
                availableMethods.add(BACKUP_CODE_METHOD);
            }
            return new TwoFactorChallengeResult(preAuthToken, PreAuthTokenService.TTL_SECONDS, availableMethods);
        }

        return issueTokens(user, ipAddress, userAgent);
    }

    @Override
    @Transactional
    public TwoFactorChallengeAckResponse challengeTwoFactor(String preAuthToken, String methodName) {
        UUID userId = preAuthTokenService.getUserId(preAuthToken)
                .orElseThrow(() -> new InvalidCredentialsException("Invalid or expired pre_auth_token"));

        if (BACKUP_CODE_METHOD.equals(methodName)) {
            if (!twoFactorBackupCodeService.hasBackupCodes(userId)) {
                throw new TwoFactorMethodNotEnabledException("Backup codes are not available for this account");
            }
            preAuthTokenService.setMethod(preAuthToken, BACKUP_CODE_METHOD);
            return new TwoFactorChallengeAckResponse(BACKUP_CODE_METHOD, "Enter one of your backup codes");
        }

        TwoFactorMethod method = parseMethod(methodName);

        TwoFactorMethodEntity config = twoFactorMethodRepository.findByUserId(userId).stream()
                .filter(m -> m.getMethod() == method)
                .findFirst()
                .orElseThrow(() -> new TwoFactorMethodNotEnabledException("Method " + method + " is not enabled for this account"));

        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new InvalidCredentialsException("Invalid or expired pre_auth_token"));

        twoFactorChallengeDispatcher.challenge(user, config);
        preAuthTokenService.setMethod(preAuthToken, method.name());

        return new TwoFactorChallengeAckResponse(method.name(), "Code sent");
    }

    @Override
    @Transactional
    public AuthResult verifyTwoFactor(String preAuthToken, String code, String ipAddress, String userAgent) {
        UUID userId = preAuthTokenService.getUserId(preAuthToken)
                .orElseThrow(() -> new InvalidCredentialsException("Invalid or expired pre_auth_token"));
        String methodName = preAuthTokenService.getMethod(preAuthToken)
                .orElseThrow(() -> new InvalidCredentialsException("Call /auth/login/2fa/challenge first"));

        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new InvalidCredentialsException("Invalid or expired pre_auth_token"));

        if (BACKUP_CODE_METHOD.equals(methodName)) {
            if (!twoFactorBackupCodeService.verify(userId, code)) {
                loginAuditLogService.record(userId, LoginAuditEventType.LOGIN_FAILED, ipAddress, userAgent,
                        null, null, "INVALID_BACKUP_CODE");
                throw new InvalidCredentialsException("Invalid backup code");
            }
            preAuthTokenService.delete(preAuthToken);
            return issueTokens(user, ipAddress, userAgent);
        }

        TwoFactorMethod method = parseMethod(methodName);
        TwoFactorMethodEntity config = twoFactorMethodRepository.findByUserId(userId).stream()
                .filter(m -> m.getMethod() == method)
                .findFirst()
                .orElseThrow(() -> new InvalidCredentialsException("Invalid or expired pre_auth_token"));

        if (!twoFactorChallengeDispatcher.verify(user, config, code)) {
            loginAuditLogService.record(userId, LoginAuditEventType.LOGIN_FAILED, ipAddress, userAgent,
                    null, null, "INVALID_2FA_CODE");
            throw new InvalidCredentialsException("Invalid verification code");
        }

        preAuthTokenService.delete(preAuthToken);
        return issueTokens(user, ipAddress, userAgent);
    }

    private AuthResult issueTokens(UserEntity user, String ipAddress, String userAgent) {
        String rawRefreshToken = UUID.randomUUID().toString();
        String tokenHash = sha256Hex(rawRefreshToken);
        Instant expiresAt = Instant.now().plusSeconds(REFRESH_TOKEN_TTL_SECONDS);
        GeoLookupResult geo = geoIpService.lookup(ipAddress);

        UserSessionEntity session = new UserSessionEntity(
                tokenHash, user.getId(), ipAddress, userAgent, geo.country(), geo.city(), expiresAt);
        userSessionRepository.save(session);

        loginAuditLogService.record(user.getId(), LoginAuditEventType.LOGIN_SUCCESS, ipAddress, userAgent,
                null, session.getId(), null);

        user.recordLogin();
        userRepository.save(user);

        String accessToken = jwtTokenProvider.generateAccessToken(user.getId());

        return new AuthResult(
                UserResponse.from(user),
                accessToken,
                rawRefreshToken,
                jwtTokenProvider.getAccessTokenTtlSeconds(),
                REFRESH_TOKEN_TTL_SECONDS
        );
    }

    private TwoFactorMethod parseMethod(String methodName) {
        try {
            return TwoFactorMethod.valueOf(methodName);
        } catch (IllegalArgumentException e) {
            throw new TwoFactorMethodNotEnabledException("Unknown 2FA method: " + methodName);
        }
    }

    /** Looked up in order username -> email -> phone — LoginRequest.identifier can be any of the three. */
    private Optional<UserEntity> findByIdentifier(String identifier) {
        return userRepository.findByUsernameAndDeletedAtIsNull(identifier)
                .or(() -> userRepository.findByEmailAndDeletedAtIsNull(identifier))
                .or(() -> userRepository.findByPhoneAndDeletedAtIsNull(identifier));
    }

    private String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }
}
