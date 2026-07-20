package com.abhay.url_shortener.util;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;

/**
 * Generates unique short codes for URL shortening.
 *
 * Strategy — two complementary approaches:
 *
 * 1. Random Base62 (default):
 *    - Generates a cryptographically random long, then Base62-encodes it.
 *    - SecureRandom is used (not Math.random or ThreadLocalRandom) to prevent
 *      code enumeration attacks where an attacker guesses sequential codes.
 *    - The service layer handles the rare collision case by retrying with a
 *      new random code (probability ≈ n / 62^7 where n = existing record count).
 *
 * 2. Counter-based (alternative, commented):
 *    - Encode a monotonically increasing DB ID with Base62.
 *    - Produces shorter codes early on and guarantees no collisions.
 *    - Downside: exposes business volume (code "A" was the first URL).
 *    - Suitable for internal tooling; not ideal for public services.
 *
 * The random approach is used here because it provides better privacy.
 */
@Component
public class ShortCodeGenerator {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    @Value("${app.short-code-length:7}")
    private int shortCodeLength;

    /**
     * Generates a random Base62 short code of the configured length.
     * Each call produces an independent random result.
     */
    public String generateRandomCode() {
        return generateRandomCode(shortCodeLength);
    }

    /**
     * Generates a random Base62 short code of a specific length.
     * Useful for tests or overrides.
     */
    public String generateRandomCode(int length) {
        // Build a random code character by character from the Base62 alphabet.
        // Using SecureRandom ensures codes are unpredictable.
        StringBuilder sb = new StringBuilder(length);
        String alphabet = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
        for (int i = 0; i < length; i++) {
            sb.append(alphabet.charAt(SECURE_RANDOM.nextInt(alphabet.length())));
        }
        return sb.toString();
    }

    /**
     * Encodes a DB-generated numeric ID into a Base62 short code.
     * Use this when you want deterministic, collision-free codes tied to IDs.
     *
     * @param id the auto-generated entity ID
     * @return Base62-encoded short code
     */
    public String encodeId(long id) {
        return Base62Encoder.encode(id);
    }

    /**
     * Returns the configured default code length.
     */
    public int getShortCodeLength() {
        return shortCodeLength;
    }
}
