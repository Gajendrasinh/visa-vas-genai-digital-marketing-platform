package com.vasmarketing.platform.idempotency;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * @param retention how long a completed response can be replayed
 * @param maxBodyBytes largest request body that is hashed and accepted on idempotent requests
 * @param schema database schema holding the idempotency table (the service's own schema)
 */
@ConfigurationProperties("platform.idempotency")
public record IdempotencyProperties(
    @DefaultValue("24h") Duration retention,
    @DefaultValue("1048576") int maxBodyBytes,
    String schema) {}
