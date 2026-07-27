package com.chatapp.core.security;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

import org.springframework.stereotype.Component;

import com.nimbusds.jose.jwk.RSAKey;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Gen 1 lần lúc thiếu file, sau đó luôn ĐỌC lại từ disk lúc khởi động — không gen key mới mỗi
 * lần restart (sẽ khiến mọi access_token cũ ký bằng key trước đó lập tức verify fail).
 * Xem giải thích cơ chế đầy đủ trong lịch sử trao đổi ở `skills/authentication.md`.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class JwtKeyManager {

    private static final int RSA_KEY_SIZE = 2048;

    private final JwtProperties properties;
    private RSAKey rsaKey;

    @PostConstruct
    public void init() throws Exception {
        Path privateKeyPath = Path.of(properties.getPrivateKeyPath());
        Path publicKeyPath = Path.of(properties.getPublicKeyPath());

        if (Files.exists(privateKeyPath) && Files.exists(publicKeyPath)) {
            this.rsaKey = loadFromDisk(privateKeyPath, publicKeyPath);
            log.info("Đã load cặp RSA key hiện có từ '{}' / '{}' (kid={})",
                    privateKeyPath, publicKeyPath, properties.getKeyId());
        } else {
            this.rsaKey = generateAndPersist(privateKeyPath, publicKeyPath);
            log.warn("Chưa có cặp RSA key — đã TỰ GEN mới và lưu vào '{}' / '{}' (kid={}). "
                            + "Chỉ chấp nhận được cho dev/local — production phải dùng key thật quản lý qua secret manager.",
                    privateKeyPath, publicKeyPath, properties.getKeyId());
        }
    }

    /** Key đầy đủ (private+public) dùng để KÝ access token — chỉ JwtTokenProvider dùng. */
    public RSAKey getSigningKey() {
        return rsaKey;
    }

    /** Chỉ phần public — dùng để trả về ở JWKS endpoint, KHÔNG bao giờ lộ private key. */
    public RSAKey getPublicJwk() {
        return rsaKey.toPublicJWK();
    }

    public String getKeyId() {
        return properties.getKeyId();
    }

    private RSAKey loadFromDisk(Path privateKeyPath, Path publicKeyPath) throws Exception {
        RSAPrivateKey privateKey = readPrivateKey(privateKeyPath);
        RSAPublicKey publicKey = readPublicKey(publicKeyPath);

        return new RSAKey.Builder(publicKey)
                .privateKey(privateKey)
                .keyID(properties.getKeyId())
                .build();
    }

    private RSAKey generateAndPersist(Path privateKeyPath, Path publicKeyPath) throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(RSA_KEY_SIZE);
        KeyPair keyPair = generator.generateKeyPair();

        Files.createDirectories(privateKeyPath.toAbsolutePath().getParent());
        Files.createDirectories(publicKeyPath.toAbsolutePath().getParent());

        writePem(privateKeyPath, "PRIVATE KEY", keyPair.getPrivate().getEncoded());
        writePem(publicKeyPath, "PUBLIC KEY", keyPair.getPublic().getEncoded());

        return new RSAKey.Builder((RSAPublicKey) keyPair.getPublic())
                .privateKey((RSAPrivateKey) keyPair.getPrivate())
                .keyID(properties.getKeyId())
                .build();
    }

    private RSAPrivateKey readPrivateKey(Path path) throws IOException, NoSuchAlgorithmException, InvalidKeySpecException {
        byte[] der = readPemBody(path);
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        return (RSAPrivateKey) keyFactory.generatePrivate(new PKCS8EncodedKeySpec(der));
    }

    private RSAPublicKey readPublicKey(Path path) throws IOException, NoSuchAlgorithmException, InvalidKeySpecException {
        byte[] der = readPemBody(path);
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        return (RSAPublicKey) keyFactory.generatePublic(new X509EncodedKeySpec(der));
    }

    private byte[] readPemBody(Path path) throws IOException {
        String pem = Files.readString(path);
        String base64 = pem
                .replaceAll("-----BEGIN [A-Z ]+-----", "")
                .replaceAll("-----END [A-Z ]+-----", "")
                .replaceAll("\\s", "");
        return Base64.getDecoder().decode(base64);
    }

    private void writePem(Path path, String label, byte[] der) throws IOException {
        String base64 = Base64.getEncoder().encodeToString(der);
        StringBuilder pem = new StringBuilder();
        pem.append("-----BEGIN ").append(label).append("-----\n");
        for (int i = 0; i < base64.length(); i += 64) {
            pem.append(base64, i, Math.min(i + 64, base64.length())).append("\n");
        }
        pem.append("-----END ").append(label).append("-----\n");
        Files.writeString(path, pem.toString());
    }
}
