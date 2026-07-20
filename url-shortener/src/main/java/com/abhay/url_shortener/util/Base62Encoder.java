package com.abhay.url_shortener.util;

/**
 * Base62 encoding / decoding utility.
 *
 * Why Base62?
 * - Alphabet: [0-9 A-Z a-z] — 62 symbols, all URL-safe, no percent-encoding needed.
 * - A 7-character Base62 code gives 62^7 ≈ 3.5 billion unique values, enough for
 *   a URL shortener at massive scale without collisions in the foreseeable future.
 * - Compared with Base64, Base62 avoids '+', '/', and '=' which require URL encoding.
 * - Compared with hex (Base16), Base62 is far more compact for the same keyspace.
 *
 * This class is stateless and thread-safe — all methods are static.
 */
public final class Base62Encoder {

    // 62 URL-safe characters: digits → uppercase → lowercase
    private static final String ALPHABET = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
    private static final int BASE = ALPHABET.length(); // 62

    // Prevent instantiation
    private Base62Encoder() {}

    /**
     * Encodes a non-negative long to a Base62 string.
     *
     * @param value a non-negative number
     * @return Base62 representation
     * @throws IllegalArgumentException if value is negative
     */
    public static String encode(long value) {
        if (value < 0) {
            throw new IllegalArgumentException("Value must be non-negative, got: " + value);
        }
        if (value == 0) {
            return String.valueOf(ALPHABET.charAt(0));
        }

        StringBuilder sb = new StringBuilder();
        long remaining = value;
        while (remaining > 0) {
            sb.append(ALPHABET.charAt((int) (remaining % BASE)));
            remaining /= BASE;
        }
        // Characters were appended in least-significant-first order — reverse.
        return sb.reverse().toString();
    }

    /**
     * Decodes a Base62 string back to a long.
     *
     * @param encoded Base62 string
     * @return decoded long value
     * @throws IllegalArgumentException if the string contains invalid characters
     */
    public static long decode(String encoded) {
        if (encoded == null || encoded.isEmpty()) {
            throw new IllegalArgumentException("Encoded string must not be null or empty");
        }

        long result = 0;
        for (char c : encoded.toCharArray()) {
            int index = ALPHABET.indexOf(c);
            if (index == -1) {
                throw new IllegalArgumentException(
                    "Invalid Base62 character '" + c + "' in input: " + encoded
                );
            }
            result = result * BASE + index;
        }
        return result;
    }

    /**
     * Returns true if the given string consists entirely of Base62 characters.
     */
    public static boolean isValidBase62(String value) {
        if (value == null || value.isEmpty()) {
            return false;
        }
        for (char c : value.toCharArray()) {
            if (ALPHABET.indexOf(c) == -1) {
                return false;
            }
        }
        return true;
    }
}
