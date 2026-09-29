package com.vasmarketing.customer.infrastructure.crypto;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.HexFormat;
import java.util.Locale;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

/**
 * Keyed hash (HMAC-SHA256) of a normalized email, scoped by tenant. Enables exact-match lookups and
 * uniqueness without storing or decrypting the address, and without letting the same address be
 * linked across tenants.
 */
@Component
public final class EmailFingerprint {

  private static final String ALGORITHM = "HmacSHA256";

  private final SecretKeySpec key;

  public EmailFingerprint(PiiProperties properties) {
    this.key =
        new SecretKeySpec(KeyMaterial.decode("PII HMAC key", properties.hmacKey()), ALGORITHM);
  }

  public String of(String tenantId, String email) {
    try {
      Mac mac = Mac.getInstance(ALGORITHM);
      mac.init(key);
      String normalized = tenantId + ":" + email.trim().toLowerCase(Locale.ROOT);
      return HexFormat.of().formatHex(mac.doFinal(normalized.getBytes(StandardCharsets.UTF_8)));
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException("email fingerprint failed", e);
    }
  }
}
