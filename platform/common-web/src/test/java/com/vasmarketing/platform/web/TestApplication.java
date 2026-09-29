package com.vasmarketing.platform.web;

import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;

/**
 * Minimal boot configuration for test slices. It deliberately does not component-scan, so the
 * library's beans are only present through its auto-configuration, as they are in services.
 */
@SpringBootConfiguration
@EnableAutoConfiguration
public class TestApplication {}
