package com.vasmarketing.customer.infrastructure.crypto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;
import org.junit.jupiter.api.Test;

class FieldCipherTest {

  static String randomKey() {
    byte[] key = new byte[32];
    new SecureRandom().nextBytes(key);
    return Base64.getEncoder().encodeToString(key);
  }

  private static PiiProperties properties(int active, Map<String, String> keys) {
    return new PiiProperties(active, keys, randomKey());
  }

  @Test
  void roundTripsAndProducesDistinctCiphertexts() {
    FieldCipher cipher = new FieldCipher(properties(1, Map.of("1", randomKey())));

    byte[] first = cipher.encrypt("jane@example.com", "customers:t:1:email");
    byte[] second = cipher.encrypt("jane@example.com", "customers:t:1:email");

    assertThat(first).isNotEqualTo(second);
    assertThat(new String(first, StandardCharsets.ISO_8859_1)).doesNotContain("jane");
    assertThat(cipher.decrypt(first, "customers:t:1:email")).isEqualTo("jane@example.com");
  }

  @Test
  void ciphertextMovedToAnotherRowOrFieldFailsAuthentication() {
    FieldCipher cipher = new FieldCipher(properties(1, Map.of("1", randomKey())));
    byte[] stored = cipher.encrypt("Jane Doe", "customers:t:1:full_name");

    assertThatThrownBy(() -> cipher.decrypt(stored, "customers:t:2:full_name"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("PII decryption failed");
    assertThatThrownBy(() -> cipher.decrypt(stored, "customers:t:1:email"))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void rotationKeepsOldCiphertextReadable() {
    String v1 = randomKey();
    byte[] underV1 = new FieldCipher(properties(1, Map.of("1", v1))).encrypt("secret", "aad");

    FieldCipher rotated = new FieldCipher(properties(2, Map.of("1", v1, "2", randomKey())));

    assertThat(rotated.decrypt(underV1, "aad")).isEqualTo("secret");
    assertThat(rotated.encrypt("secret", "aad")[0]).isEqualTo((byte) 2);
    FieldCipher withoutV1 = new FieldCipher(properties(2, Map.of("2", randomKey())));
    assertThatThrownBy(() -> withoutV1.decrypt(underV1, "aad")).hasMessageContaining("version 1");
  }

  @Test
  void rejectsMissingOrWeakKeys() {
    assertThatThrownBy(() -> new FieldCipher(properties(1, Map.of("1", "c2hvcnQ="))))
        .hasMessageContaining("openssl rand -base64 32");
    assertThatThrownBy(() -> new FieldCipher(properties(1, Map.of("1", "%%%"))))
        .hasMessageContaining("256-bit");
    assertThatThrownBy(() -> new FieldCipher(properties(2, Map.of("1", randomKey()))))
        .hasMessageContaining("active PII key version 2");
    assertThatThrownBy(() -> new FieldCipher(properties(0, Map.of("0", randomKey()))))
        .hasMessageContaining("1-127");
  }

  @Test
  void emailFingerprintIsTenantScopedAndNormalized() {
    EmailFingerprint fingerprint =
        new EmailFingerprint(new PiiProperties(1, Map.of(), randomKey()));

    assertThat(fingerprint.of("tenant-a", " Jane@Example.com "))
        .isEqualTo(fingerprint.of("tenant-a", "jane@example.com"));
    assertThat(fingerprint.of("tenant-a", "jane@example.com"))
        .isNotEqualTo(fingerprint.of("tenant-b", "jane@example.com"));
    assertThat(fingerprint.of("tenant-a", "jane@example.com")).hasSize(64);
  }
}
