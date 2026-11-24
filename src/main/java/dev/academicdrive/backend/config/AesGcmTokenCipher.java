package dev.academicdrive.backend.config;

import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class AesGcmTokenCipher {
    private static final String VERSION = "v1";
    private static final int IV_LENGTH = 12;
    private static final int TAG_LENGTH_BITS = 128;

    private final SecretKeySpec key;
    private final SecureRandom secureRandom = new SecureRandom();

    public AesGcmTokenCipher(@Value("${security.token-encryption-key}") String encodedKey) {
        byte[] keyBytes;
        try {
            keyBytes = Base64.getDecoder().decode(encodedKey);
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("TOKEN_ENCRYPTION_KEY must be a Base64-encoded 32-byte key.", exception);
        }
        if (keyBytes.length != 32) {
            throw new IllegalStateException("TOKEN_ENCRYPTION_KEY must be a Base64-encoded 32-byte key.");
        }
        this.key = new SecretKeySpec(keyBytes, "AES");
    }

    public String encrypt(String plaintext) {
        byte[] iv = new byte[IV_LENGTH];
        secureRandom.nextBytes(iv);
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return VERSION + ":" + Base64.getEncoder().encodeToString(ByteBuffer.allocate(iv.length + ciphertext.length)
                    .put(iv)
                    .put(ciphertext)
                    .array());
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Could not encrypt the OAuth token.", exception);
        }
    }

    public String decrypt(String encryptedValue) {
        String[] parts = encryptedValue.split(":", 2);
        if (parts.length != 2 || !VERSION.equals(parts[0])) {
            throw new IllegalStateException("Stored OAuth token has an unsupported encryption format.");
        }

        byte[] payload;
        try {
            payload = Base64.getDecoder().decode(parts[1]);
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("Stored OAuth token is not valid encrypted data.", exception);
        }
        if (payload.length <= IV_LENGTH) {
            throw new IllegalStateException("Stored OAuth token is incomplete.");
        }

        byte[] iv = java.util.Arrays.copyOfRange(payload, 0, IV_LENGTH);
        byte[] ciphertext = java.util.Arrays.copyOfRange(payload, IV_LENGTH, payload.length);
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            return new String(cipher.doFinal(ciphertext), java.nio.charset.StandardCharsets.UTF_8);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Could not decrypt the stored OAuth token.", exception);
        }
    }
}
