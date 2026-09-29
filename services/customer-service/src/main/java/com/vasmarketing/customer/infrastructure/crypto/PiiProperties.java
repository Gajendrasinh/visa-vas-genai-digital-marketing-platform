package com.vasmarketing.customer.infrastructure.crypto;

import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * PII key material (base64, 256-bit). Keys are versioned so they can be rotated: new data uses the
 * active version, older ciphertext stays readable while its key is still listed.
 */
@ConfigurationProperties("customer.pii")
public record PiiProperties(int activeKeyVersion, Map<String, String> keys, String hmacKey) {

  public PiiProperties {
    keys = keys == null ? Map.of() : Map.copyOf(keys);
  }
}
