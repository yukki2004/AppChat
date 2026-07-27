package com.chatapp.core.auth.impl;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.chatapp.core.auth.AuthResult;
import com.chatapp.core.auth.AuthService;
import com.chatapp.core.auth.dto.request.LoginRequest;
import com.chatapp.core.auth.dto.request.RegisterRequest;
import com.chatapp.core.auth.dto.response.PublicUserDTO;
import com.chatapp.core.auth.entity.UserSessionEntity;
import com.chatapp.core.auth.repository.UserSessionRepository;
import com.chatapp.core.exception.DuplicateUserException;
import com.chatapp.core.exception.InvalidCredentialsException;
import com.chatapp.core.security.JwtTokenProvider;
import com.chatapp.core.user.UserEntity;
import com.chatapp.core.user.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private static final long REFRESH_TOKEN_TTL_SECONDS = 2_592_000; // 30 ngày, xem 05-cookie-auth-flow.md E.1

    private final UserRepository userRepository;
    private final UserSessionRepository userSessionRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

    @Override
    @Transactional
    public PublicUserDTO register(RegisterRequest request) {
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new DuplicateUserException("Username đã tồn tại");
        }
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new DuplicateUserException("Email đã được đăng ký");
        }

        String passwordHash = passwordEncoder.encode(request.getPassword());
        UserEntity user = new UserEntity(request.getUsername(), request.getEmail(), passwordHash, request.getDisplayName());
        UserEntity saved = userRepository.save(user);

        return PublicUserDTO.from(saved);
    }

    @Override
    @Transactional
    public AuthResult login(LoginRequest request, String ipAddress, String userAgent) {
        UserEntity user = userRepository.findByUsernameAndDeletedAtIsNull(request.getUsernameOrEmail())
                .or(() -> userRepository.findByEmailAndDeletedAtIsNull(request.getUsernameOrEmail()))
                .orElseThrow(() -> new InvalidCredentialsException("Sai tài khoản hoặc mật khẩu"));

        if (user.getPasswordHash() == null || !passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new InvalidCredentialsException("Sai tài khoản hoặc mật khẩu");
        }
        if (user.isBlocked() || !user.isActive()) {
            throw new InvalidCredentialsException("Tài khoản đã bị khoá hoặc vô hiệu hoá");
        }

        String rawRefreshToken = UUID.randomUUID().toString();
        String tokenHash = sha256Hex(rawRefreshToken);
        Instant expiresAt = Instant.now().plusSeconds(REFRESH_TOKEN_TTL_SECONDS);

        userSessionRepository.save(new UserSessionEntity(tokenHash, user.getId(), ipAddress, userAgent, expiresAt));

        user.recordLogin();
        userRepository.save(user);

        String accessToken = jwtTokenProvider.generateAccessToken(user.getId());

        return new AuthResult(
                PublicUserDTO.from(user),
                accessToken,
                rawRefreshToken,
                jwtTokenProvider.getAccessTokenTtlSeconds(),
                REFRESH_TOKEN_TTL_SECONDS
        );
    }

    private String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 không khả dụng", e);
        }
    }
}
