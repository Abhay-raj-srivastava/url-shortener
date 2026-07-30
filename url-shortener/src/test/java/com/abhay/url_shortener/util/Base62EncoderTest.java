package com.abhay.url_shortener.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for Base62Encoder.
 * These are pure logic tests — no Spring context needed.
 */
@DisplayName("Base62Encoder Unit Tests")
class Base62EncoderTest {

    @Test
    @DisplayName("should encode 0 to '0'")
    void shouldEncodeZero() {
        assertThat(Base62Encoder.encode(0)).isEqualTo("0");
    }

    @Test
    @DisplayName("should encode and decode symmetrically")
    void shouldEncodeAndDecodeSymmetrically() {
        long[] testValues = {1L, 10L, 62L, 100L, 1_000L, 1_000_000L, Long.MAX_VALUE / 1000};
        for (long value : testValues) {
            String encoded = Base62Encoder.encode(value);
            long decoded = Base62Encoder.decode(encoded);
            assertThat(decoded)
                    .as("Round-trip for value %d", value)
                    .isEqualTo(value);
        }
    }

    @Test
    @DisplayName("should throw IllegalArgumentException for negative values")
    void shouldThrowForNegativeValue() {
        assertThatThrownBy(() -> Base62Encoder.encode(-1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("non-negative");
    }

    @Test
    @DisplayName("should throw IllegalArgumentException for invalid Base62 characters")
    void shouldThrowForInvalidCharacters() {
        assertThatThrownBy(() -> Base62Encoder.decode("abc!"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid Base62 character");
    }

    @Test
    @DisplayName("should throw for null or empty input to decode")
    void shouldThrowForNullOrEmptyDecode() {
        assertThatThrownBy(() -> Base62Encoder.decode(null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Base62Encoder.decode(""))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"abc123", "ABC", "0", "zZ9"})
    @DisplayName("should validate correct Base62 strings")
    void shouldValidateValidStrings(String input) {
        assertThat(Base62Encoder.isValidBase62(input)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"abc!", "hello world", "abc-123", ""})
    @DisplayName("should reject invalid Base62 strings")
    void shouldRejectInvalidStrings(String input) {
        assertThat(Base62Encoder.isValidBase62(input)).isFalse();
    }

    @Test
    @DisplayName("encoded values should only contain Base62 characters")
    void encodedValuesShouldOnlyContainBase62Chars() {
        for (long i = 0; i < 1000; i++) {
            String encoded = Base62Encoder.encode(i);
            assertThat(Base62Encoder.isValidBase62(encoded))
                    .as("Encoded value of %d ('%s') should be valid Base62", i, encoded)
                    .isTrue();
        }
    }

    @Test
    @DisplayName("higher values should produce same or longer codes")
    void higherValuesShouldProduceSameOrLongerCodes() {
        String small = Base62Encoder.encode(1);
        String large = Base62Encoder.encode(1_000_000_000L);
        assertThat(large.length()).isGreaterThanOrEqualTo(small.length());
    }
}
