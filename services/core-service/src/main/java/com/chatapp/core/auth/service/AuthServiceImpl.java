package com.chatapp.core.auth.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.chatapp.core.auth.AuthService;
import com.chatapp.core.auth.dto.request.LoginRequest;
import com.chatapp.core.auth.dto.request.RegisterRequest;
import com.chatapp.core.auth.dto.response.SessionResponse;
import com.chatapp.core.auth.dto.response.TwoFactorChallengeAckResponse;
import com.chatapp.core.auth.result.AuthResult;
import com.chatapp.core.auth.result.LoginOutcome;
import com.chatapp.core.auth.result.TwoFactorChallengeResult;
import com.chatapp.core.audit.LoginAuditLogService;
import com.chatapp.core.base.UserResponse;
import com.chatapp.core.base.util.HashUtils;
import com.chatapp.core.base.constant.LoginAuditEventType;
import com.chatapp.core.base.constant.OtpPurpose;
import com.chatapp.core.base.constant.TwoFactorMethod;
import com.chatapp.core.base.entity.TwoFactorMethodEntity;
import com.chatapp.core.base.entity.UserEntity;
import com.chatapp.core.base.entity.UserSessionEntity;
import com.chatapp.core.base.repository.TwoFactorMethodRepository;
import com.chatapp.core.base.repository.UserRepository;
import com.chatapp.core.base.repository.UserSessionRepository;
import com.chatapp.core.exception.DuplicateUserException;
import com.chatapp.core.exception.InvalidCredentialsException;
import com.chatapp.core.exception.RefreshTokenInvalidException;
import com.chatapp.core.exception.SessionNotFoundException;
import com.chatapp.core.exception.TwoFactorMethodNotEnabledException;
import com.chatapp.core.exception.common.AppException;
import com.chatapp.core.exception.common.ErrorCode;
import com.chatapp.core.geoip.GeoIpService;
import com.chatapp.core.geoip.GeoLookupResult;
import com.chatapp.core.security.JwtRevocationService;
import com.chatapp.core.security.JwtTokenProvider;
import com.chatapp.core.twofactor.backupcode.TwoFactorBackupCodeService;
import com.chatapp.core.twofactor.otp.OtpCodeService;
import com.chatapp.core.twofactor.otp.OtpMailSender;
import com.chatapp.core.twofactor.strategy.TwoFactorChallengeDispatcher;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthServiceImpl implements AuthService {

    private static final long REFRESH_TOKEN_TTL_SECONDS = 2_592_000;

    private static final String BACKUP_CODE_METHOD = "BACKUP_CODE";

    private static final String ROTATED_REVOKE_REASON = "ROTATED";

    private final UserRepository userRepository;
    private final UserSessionRepository userSessionRepository;
    private final TwoFactorMethodRepository twoFactorMethodRepository;
    private final TwoFactorChallengeDispatcher twoFactorChallengeDispatcher;
    private final TwoFactorBackupCodeService twoFactorBackupCodeService;
    private final PreAuthTokenService preAuthTokenService;
    private final ResetPasswordTokenService resetPasswordTokenService;
    private final OtpCodeService otpCodeService;
    private final OtpMailSender otpMailSender;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final JwtRevocationService jwtRevocationService;
    private final GeoIpService geoIpService;
    private final LoginAuditLogService loginAuditLogService;

    @Override
    @Transactional
    public UserResponse register(RegisterRequest request) {
        log.debug("register start username={} hasEmail={} hasPhone={}",
                request.getUsername(), request.getEmail() != null, request.getPhone() != null);

        if (userRepository.existsByUsername(request.getUsername())) {
            log.warn("register rejected username={} reason=USERNAME_TAKEN", request.getUsername());
            throw new DuplicateUserException("Username already exists");
        }
        if (request.getEmail() != null && userRepository.existsByEmail(request.getEmail())) {
            log.warn("register rejected username={} reason=EMAIL_TAKEN", request.getUsername());
            throw new DuplicateUserException("Email is already registered");
        }
        if (request.getPhone() != null && userRepository.existsByPhone(request.getPhone())) {
            log.warn("register rejected username={} reason=PHONE_TAKEN", request.getUsername());
            throw new DuplicateUserException("Phone number is already registered");
        }

        String passwordHash = passwordEncoder.encode(request.getPassword());
        UserEntity user = new UserEntity(
                request.getUsername(), request.getEmail(), request.getPhone(), passwordHash, request.getDisplayName());
        UserEntity saved = userRepository.save(user);

        log.info("register success userId={} username={}", saved.getId(), saved.getUsername());
        return UserResponse.from(saved);
    }

    @Override
    @Transactional
    public LoginOutcome login(LoginRequest request, String ipAddress, String userAgent) {
        log.debug("login start identifier={} ip={}", request.getIdentifier(), ipAddress);

        Optional<UserEntity> foundUser = findByIdentifier(request.getIdentifier());
        if (foundUser.isEmpty()) {
            log.warn("login failed identifier={} reason=UNKNOWN_IDENTIFIER", request.getIdentifier());
            loginAuditLogService.record(null, LoginAuditEventType.LOGIN_FAILED, ipAddress, userAgent,
                    null, null, "UNKNOWN_IDENTIFIER");
            throw new InvalidCredentialsException("Invalid username/email/phone or password");
        }
        UserEntity user = foundUser.get();

        if (user.getPasswordHash() == null || !passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            log.warn("login failed userId={} reason=WRONG_PASSWORD", user.getId());
            loginAuditLogService.record(user.getId(), LoginAuditEventType.LOGIN_FAILED, ipAddress, userAgent,
                    null, null, "WRONG_PASSWORD");
            throw new InvalidCredentialsException("Invalid username/email/phone or password");
        }
        if (user.isBlocked() || !user.isActive()) {
            log.warn("login failed userId={} reason=ACCOUNT_BLOCKED", user.getId());
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
            log.info("login password OK, 2FA required userId={} availableMethods={}", user.getId(), availableMethods);
            return new TwoFactorChallengeResult(preAuthToken, PreAuthTokenService.TTL_SECONDS, availableMethods);
        }

        log.info("login success (no 2FA) userId={}", user.getId());
        return issueTokens(user, ipAddress, userAgent);
    }

    @Override
    @Transactional
    public TwoFactorChallengeAckResponse challengeTwoFactor(String preAuthToken, String methodName) {
        log.debug("challengeTwoFactor start method={}", methodName);
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

        log.info("challengeTwoFactor code sent userId={} method={}", userId, method);
        return new TwoFactorChallengeAckResponse(method.name(), "Code sent");
    }

    @Override
    @Transactional
    public AuthResult verifyTwoFactor(String preAuthToken, String code, String ipAddress, String userAgent) {
        UUID userId = preAuthTokenService.getUserId(preAuthToken)
                .orElseThrow(() -> new InvalidCredentialsException("Invalid or expired pre_auth_token"));
        String methodName = preAuthTokenService.getMethod(preAuthToken)
                .orElseThrow(() -> new InvalidCredentialsException("Call /auth/login/2fa/challenge first"));
        log.debug("verifyTwoFactor start userId={} method={}", userId, methodName);

        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new InvalidCredentialsException("Invalid or expired pre_auth_token"));

        if (BACKUP_CODE_METHOD.equals(methodName)) {
            if (!twoFactorBackupCodeService.verify(userId, code)) {
                log.warn("verifyTwoFactor failed userId={} method=BACKUP_CODE reason=INVALID_CODE", userId);
                loginAuditLogService.record(userId, LoginAuditEventType.LOGIN_FAILED, ipAddress, userAgent,
                        null, null, "INVALID_BACKUP_CODE");
                throw new InvalidCredentialsException("Invalid backup code");
            }
            preAuthTokenService.delete(preAuthToken);
            log.info("verifyTwoFactor success userId={} method=BACKUP_CODE", userId);
            return issueTokens(user, ipAddress, userAgent);
        }

        TwoFactorMethod method = parseMethod(methodName);
        TwoFactorMethodEntity config = twoFactorMethodRepository.findByUserId(userId).stream()
                .filter(m -> m.getMethod() == method)
                .findFirst()
                .orElseThrow(() -> new InvalidCredentialsException("Invalid or expired pre_auth_token"));

        if (!twoFactorChallengeDispatcher.verify(user, config, code)) {
            log.warn("verifyTwoFactor failed userId={} method={} reason=INVALID_CODE", userId, method);
            loginAuditLogService.record(userId, LoginAuditEventType.LOGIN_FAILED, ipAddress, userAgent,
                    null, null, "INVALID_2FA_CODE");
            throw new InvalidCredentialsException("Invalid verification code");
        }

        preAuthTokenService.delete(preAuthToken);
        log.info("verifyTwoFactor success userId={} method={}", userId, method);
        return issueTokens(user, ipAddress, userAgent);
    }

    @Override
    @Transactional
    public AuthResult refreshToken(String refreshToken, String ipAddress, String userAgent) {
        if (refreshToken == null || refreshToken.isBlank()) {
            log.warn("refreshToken rejected reason=MISSING_TOKEN");
            throw new RefreshTokenInvalidException("Missing refresh_token");
        }
        String tokenHash = HashUtils.sha256Hex(refreshToken);
        Instant now = Instant.now();

        UserSessionEntity session = userSessionRepository
                .findByTokenHashAndIsActiveTrueAndExpiresAtAfter(tokenHash, now)
                .orElse(null);

        if (session == null) {
            log.warn("refreshToken rejected reason=NOT_FOUND_OR_EXPIRED (checking for reuse)");
            detectReuseAndRevokeAll(tokenHash, now);
            throw new RefreshTokenInvalidException("Invalid or expired refresh_token");
        }

        session.revoke(ROTATED_REVOKE_REASON);

        UserEntity user = userRepository.findById(session.getUserId())
                .orElseThrow(() -> new RefreshTokenInvalidException("Invalid or expired refresh_token"));

        log.info("refreshToken success userId={} oldSessionId={}", user.getId(), session.getId());
        return issueTokens(user, session, ipAddress, userAgent);
    }

    private void detectReuseAndRevokeAll(String tokenHash, Instant now) {
        userSessionRepository.findByTokenHash(tokenHash)
                .filter(revoked -> ROTATED_REVOKE_REASON.equals(revoked.getRevokeReason()))
                .ifPresent(revoked -> {
                    UUID userId = revoked.getUserId();
                    log.warn("refresh_token reuse detected userId={} sessionId={} — revoking every active session",
                            userId, revoked.getId());
                    for (UserSessionEntity active : userSessionRepository
                            .findAllByUserIdAndIsActiveTrueAndExpiresAtAfter(userId, now)) {
                        active.revoke("REFRESH_TOKEN_REUSE_DETECTED");
                    }
                    jwtRevocationService.revokeAllForUser(userId, jwtTokenProvider.getAccessTokenTtlSeconds());
                    // TODO(outbox-pattern): publish a security-alert event on user.exchange once
                    // the outbox table + relay worker exist (skills/outbox-pattern.md) so
                    // Notification Service can warn the user — no direct RabbitMQ publish here
                    // in the meantime.
                });
    }

    @Override
    @Transactional
    public void logout(String refreshToken, String accessToken, String ipAddress, String userAgent) {
        log.debug("logout start hasRefreshToken={} hasAccessToken={}",
                refreshToken != null && !refreshToken.isBlank(), accessToken != null && !accessToken.isBlank());

        UserSessionEntity session = null;
        if (refreshToken != null && !refreshToken.isBlank()) {
            session = userSessionRepository.findByTokenHashAndIsActiveTrueAndExpiresAtAfter(
                    HashUtils.sha256Hex(refreshToken), Instant.now()).orElse(null);
            if (session != null) {
                session.revoke("USER_LOGOUT");
            }
        }

        JwtTokenProvider.AccessTokenClaims claims = null;
        if (accessToken != null && !accessToken.isBlank()) {
            claims = jwtTokenProvider.parseAndVerify(accessToken).orElse(null);
            if (claims != null) {
                jwtRevocationService.blacklist(claims.jti(), claims.expiresAt());
            }
        }

        UUID userId = session != null ? session.getUserId() : (claims != null ? claims.userId() : null);
        if (userId != null) {
            log.info("logout success userId={} sessionId={}", userId, session != null ? session.getId() : null);
            loginAuditLogService.record(userId, LoginAuditEventType.LOGOUT, ipAddress, userAgent,
                    session != null ? session.getDeviceId() : null,
                    session != null ? session.getId() : null, null);
        } else {
            // Both cookies missing/already invalid -> nothing to revoke, nothing meaningful to
            // attribute an audit row to; logout stays a no-op success either way (idempotent).
            log.warn("logout no-op — both refresh_token and access_token missing/already invalid");
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<SessionResponse> listSessions(UUID userId, String currentRefreshToken) {
        log.debug("listSessions start userId={}", userId);
        String currentTokenHash = (currentRefreshToken != null && !currentRefreshToken.isBlank())
                ? HashUtils.sha256Hex(currentRefreshToken) : null;

        List<SessionResponse> sessions = userSessionRepository
                .findAllByUserIdAndIsActiveTrueAndExpiresAtAfter(userId, Instant.now()).stream()
                .sorted(Comparator.comparing(UserSessionEntity::getLastActiveAt).reversed())
                .map(session -> SessionResponse.from(session, session.getTokenHash().equals(currentTokenHash)))
                .toList();
        log.info("listSessions success userId={} count={}", userId, sessions.size());
        return sessions;
    }

    @Override
    @Transactional
    public void logoutSession(UUID userId, UUID sessionId, String ipAddress, String userAgent) {
        UserSessionEntity session = userSessionRepository
                .findByIdAndUserIdAndIsActiveTrueAndExpiresAtAfter(sessionId, userId, Instant.now())
                .orElseThrow(() -> new SessionNotFoundException("Session not found or already revoked"));
        session.revoke("USER_REVOKED_REMOTE");

        if (session.getAccessTokenJti() != null) {
            Instant accessTokenExpiresAt = session.getCreatedAt().plusSeconds(jwtTokenProvider.getAccessTokenTtlSeconds());
            jwtRevocationService.blacklist(session.getAccessTokenJti(), accessTokenExpiresAt);
            log.info("Blacklisted access_token jti={} immediately for remote logoutSession sessionId={} userId={}",
                    session.getAccessTokenJti(), sessionId, userId);
        } else {
            log.warn("logoutSession sessionId={} userId={} has no access_token_jti on record — "
                    + "its access_token (if still live) will only die on its own natural expiry", sessionId, userId);
        }

        loginAuditLogService.record(userId, LoginAuditEventType.SESSION_REVOKE, ipAddress, userAgent,
                session.getDeviceId(), session.getId(), null);
    }

    @Override
    @Transactional
    public void logoutAll(UUID userId, String currentRefreshToken, boolean keepCurrent, String ipAddress, String userAgent) {
        log.debug("logoutAll start userId={} keepCurrent={}", userId, keepCurrent);
        Instant now = Instant.now();
        UUID excludedSessionId = null;
        if (keepCurrent && currentRefreshToken != null && !currentRefreshToken.isBlank()) {
            excludedSessionId = userSessionRepository
                    .findByTokenHashAndIsActiveTrueAndExpiresAtAfter(HashUtils.sha256Hex(currentRefreshToken), now)
                    .map(UserSessionEntity::getId)
                    .orElse(null);
        }

        int revokedCount = 0;
        for (UserSessionEntity session : userSessionRepository.findAllByUserIdAndIsActiveTrueAndExpiresAtAfter(userId, now)) {
            if (session.getId().equals(excludedSessionId)) {
                continue;
            }
            session.revoke("USER_LOGOUT_ALL");
            revokedCount++;
            loginAuditLogService.record(userId, LoginAuditEventType.SESSION_REVOKE, ipAddress, userAgent,
                    session.getDeviceId(), session.getId(), null);
        }

        if (!keepCurrent) {
            // Blacklists every access_token already issued to this user at once (unlike
            // logoutSession, this doesn't need to know individual jti values) — see E.6.
            jwtRevocationService.revokeAllForUser(userId, jwtTokenProvider.getAccessTokenTtlSeconds());
        }

        log.info("logoutAll success userId={} revokedSessions={} keepCurrent={}", userId, revokedCount, keepCurrent);
        // TODO(outbox-pattern): publish `user.logged_out_all` on `user.exchange`
        // (RoutingKeys.UserExchange.USER_LOGGED_OUT_ALL) once the outbox table + relay worker
        // exist (skills/outbox-pattern.md) — no direct RabbitMQ publish here in the meantime.
        // WS Gateway subscribes to this to force-disconnect this user's live sockets; without
        // it, an already-open WS connection keeps working until it happens to reconnect (see
        // docs/.../05-cookie-auth-flow.md E.8).
    }

    @Override
    @Transactional
    public void changePassword(UUID userId, String oldPassword, String newPassword, String ipAddress, String userAgent) {
        log.debug("changePassword start userId={}", userId);
        UserEntity user = userRepository.findByIdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
        if (user.getPasswordHash() == null || !passwordEncoder.matches(oldPassword, user.getPasswordHash())) {
            log.warn("changePassword rejected userId={} reason=INVALID_OLD_PASSWORD", userId);
            throw new AppException(ErrorCode.INVALID_OLD_PASSWORD);
        }

        user.changePassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
        revokeAllSessions(userId);

        loginAuditLogService.record(userId, LoginAuditEventType.PASSWORD_CHANGE, ipAddress, userAgent, null, null, null);
        log.info("changePassword success userId={} — every session revoked", userId);
    }

    /** Always logs the same outcome regardless of whether the email is registered — logging
     *  "email not found" here at any level above DEBUG would let anyone with log access build
     *  an account-enumeration oracle out of the logs, defeating the whole point of the generic
     *  200 response (see AuthController). */
    @Override
    @Transactional
    public void forgotPassword(String email) {
        log.debug("forgotPassword start email={}", email);
        userRepository.findByEmailAndDeletedAtIsNull(email).ifPresent(user -> {
            String code = otpCodeService.generate(email, OtpPurpose.RESET_PASSWORD, user.getId());
            otpMailSender.send(email, code);
            log.info("forgotPassword code sent userId={}", user.getId());
        });
    }

    @Override
    @Transactional
    public String verifyPasswordResetOtp(String email, String code) {
        log.debug("verifyPasswordResetOtp start email={}", email);
        UserEntity user = userRepository.findByEmailAndDeletedAtIsNull(email)
                .orElseThrow(() -> new AppException(ErrorCode.PASSWORD_RESET_CODE_INVALID));
        if (!otpCodeService.verify(email, OtpPurpose.RESET_PASSWORD, user.getId(), code)) {
            log.warn("verifyPasswordResetOtp failed userId={} reason=INVALID_CODE", user.getId());
            throw new AppException(ErrorCode.PASSWORD_RESET_CODE_INVALID);
        }
        log.info("verifyPasswordResetOtp success userId={} — reset_token issued", user.getId());
        return resetPasswordTokenService.create(user.getId());
    }

    @Override
    @Transactional
    public void resetPassword(String resetToken, String newPassword) {
        UUID userId = resetPasswordTokenService.getUserId(resetToken)
                .orElseThrow(() -> {
                    log.warn("resetPassword rejected reason=INVALID_OR_EXPIRED_RESET_TOKEN");
                    return new AppException(ErrorCode.RESET_TOKEN_INVALID);
                });
        log.debug("resetPassword start userId={}", userId);
        UserEntity user = userRepository.findByIdAndDeletedAtIsNull(userId)
                .orElseThrow(() -> new AppException(ErrorCode.RESET_TOKEN_INVALID));

        user.changePassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
        revokeAllSessions(userId);
        resetPasswordTokenService.delete(resetToken);

        loginAuditLogService.record(userId, LoginAuditEventType.PASSWORD_CHANGE, null, null, null, null, null);
        log.info("resetPassword success userId={} — every session revoked", userId);
    }

    private void revokeAllSessions(UUID userId) {
        Instant now = Instant.now();
        for (UserSessionEntity session : userSessionRepository.findAllByUserIdAndIsActiveTrueAndExpiresAtAfter(userId, now)) {
            session.revoke("PASSWORD_CHANGED");
        }
        jwtRevocationService.revokeAllForUser(userId, jwtTokenProvider.getAccessTokenTtlSeconds());
    }

    private AuthResult issueTokens(UserEntity user, String ipAddress, String userAgent) {
        String rawRefreshToken = UUID.randomUUID().toString();
        String tokenHash = HashUtils.sha256Hex(rawRefreshToken);
        Instant expiresAt = Instant.now().plusSeconds(REFRESH_TOKEN_TTL_SECONDS);
        GeoLookupResult geo = geoIpService.lookup(ipAddress);

        UserSessionEntity session = new UserSessionEntity(
                tokenHash, user.getId(), ipAddress, userAgent, geo.country(), geo.city(), expiresAt);
        userSessionRepository.save(session);

        loginAuditLogService.record(user.getId(), LoginAuditEventType.LOGIN_SUCCESS, ipAddress, userAgent,
                null, session.getId(), null);

        user.recordLogin();
        userRepository.save(user);

        return buildAuthResult(user, session, rawRefreshToken);
    }

    private AuthResult issueTokens(UserEntity user, UserSessionEntity previousSession, String ipAddress, String userAgent) {
        String rawRefreshToken = UUID.randomUUID().toString();
        String tokenHash = HashUtils.sha256Hex(rawRefreshToken);
        Instant expiresAt = Instant.now().plusSeconds(REFRESH_TOKEN_TTL_SECONDS);
        GeoLookupResult geo = geoIpService.lookup(ipAddress);

        UserSessionEntity session = new UserSessionEntity(
                tokenHash, user.getId(),
                previousSession.getDeviceId(), previousSession.getDeviceName(), previousSession.getPlatform(),
                ipAddress, userAgent, geo.country(), geo.city(), expiresAt);
        userSessionRepository.save(session);

        return buildAuthResult(user, session, rawRefreshToken);
    }

    private AuthResult buildAuthResult(UserEntity user, UserSessionEntity session, String rawRefreshToken) {
        JwtTokenProvider.IssuedAccessToken issued = jwtTokenProvider.generateAccessToken(user.getId());
        session.assignAccessTokenJti(issued.jti());
        return new AuthResult(
                UserResponse.from(user),
                issued.token(),
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

}
