package com.vasmarketing.customer.infrastructure.crypto;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.HashMap;
import java.util.Map;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

/**
 * AES-256-GCM field encryption. Output layout: {@code [key version:1][IV:12][ciphertext+tag]}.
 *
 * <p>The associated data binds each ciphertext to its tenant, row and column, so a value copied
 * into another row or field fails authentication instead of decrypting.
 */
@Component
public final class FieldCipher {

  private static final String TRANSFORMATION = "AES/GCM/NoPadding";
  private static final int IV_BYTES = 12;
  private static final int TAG_BITS = 128;

  private final SecureRandom random = new SecureRandom();
  private final Map<Integer, SecretKey> keys = new HashMap<>();
  private final int activeVersion;

  public FieldCipher(PiiProperties properties) {
    properties
        .keys()
        .forEach(
            (version, base64) -> {
              int v = Integer.parseInt(version);
              if (v < 1 || v > Byte.MAX_VALUE) {
                throw new IllegalStateException("PII key versions must be 1-127");
              }
              keys.put(v, new SecretKeySpec(KeyMaterial.decode("PII key v" + v, base64), "AES"));
            });
    if (!keys.containsKey(properties.activeKeyVersion())) {
      throw new IllegalStateException(
          "active PII key version " + properties.activeKeyVersion() + " is not configured");
    }
    this.activeVersion = properties.activeKeyVersion();
  }

  public byte[] encrypt(String plaintext, String associatedData) {
    byte[] iv = new byte[IV_BYTES];
    random.nextBytes(iv);
    byte[] ciphertext =
        run(Cipher.ENCRYPT_MODE, keys.get(activeVersion), iv, associatedData, utf8(plaintext));
    return ByteBuffer.allocate(1 + IV_BYTES + ciphertext.length)
        .put((byte) activeVersion)
        .put(iv)
        .put(ciphertext)
        .array();
  }

  public String decrypt(byte[] stored, String associatedData) {
    ByteBuffer buffer = ByteBuffer.wrap(stored);
    int version = buffer.get();
    SecretKey key = keys.get(version);
    if (key == null) {
      throw new IllegalStateException("no PII key configured for version " + version);
    }
    byte[] iv = new byte[IV_BYTES];
    buffer.get(iv);
    byte[] ciphertext = new byte[buffer.remaining()];
    buffer.get(ciphertext);
    return new String(
        run(Cipher.DECRYPT_MODE, key, iv, associatedData, ciphertext), StandardCharsets.UTF_8);
  }

  private static byte[] run(int mode, SecretKey key, byte[] iv, String aad, byte[] input) {
    try {
      Cipher cipher = Cipher.getInstance(TRANSFORMATION);
      cipher.init(mode, key, new GCMParameterSpec(TAG_BITS, iv));
      cipher.updateAAD(utf8(aad));
      return cipher.doFinal(input);
    } catch (GeneralSecurityException e) {
      // Deliberately generic: never include plaintext or key details in errors.
      throw new IllegalStateException(
          "PII " + (mode == Cipher.ENCRYPT_MODE ? "encryption" : "decryption") + " failed", e);
    }
  }

  private static byte[] utf8(String value) {
    return value.getBytes(StandardCharsets.UTF_8);
  }
}
