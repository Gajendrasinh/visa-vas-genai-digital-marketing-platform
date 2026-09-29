package com.vasmarketing.platform.web;

import java.net.URI;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Settings for the shared web layer.
 *
 * @param problemTypeBase base URI for the {@code type} field of problem responses
 */
@ConfigurationProperties("platform.web")
public record WebPlatformProperties(
    @DefaultValue("https://errors.vasmarketing.example") URI problemTypeBase) {}
