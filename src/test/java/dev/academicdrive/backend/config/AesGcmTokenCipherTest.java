package dev.academicdrive.backend.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Base64;
import org.junit.jupiter.api.Test;

class AesGcmTokenCipherTest {
    private final AesGcmTokenCipher cipher =
            new AesGcmTokenCipher(Base64.getEncoder().encodeToString(new byte[32]));

    @Test
    void encryptsTokensWithUniqueCiphertextAndCanDecryptThem() {
        String first = cipher.encrypt("google-access-token");
        String second = cipher.encrypt("google-access-token");

        assertNotEquals(first, second);
        assertEquals("google-access-token", cipher.decrypt(first));
        assertEquals("google-access-token", cipher.decrypt(second));
    }

    @Test
    void rejectsTamperedCiphertext() {
        String encrypted = cipher.encrypt("google-refresh-token");
        String tampered = encrypted.substring(0, encrypted.length() - 2) + "AA";

        assertThrows(IllegalStateException.class, () -> cipher.decrypt(tampered));
    }

    @Test
    void refusesInvalidEncryptionKey() {
        assertThrows(IllegalStateException.class, () -> new AesGcmTokenCipher("not-a-key"));
        assertThrows(
                IllegalStateException.class,
                () -> new AesGcmTokenCipher(Base64.getEncoder().encodeToString(new byte[16])));
    }
}
