package com.vasmarketing.platform.web.error;

import java.util.Map;
import org.springframework.http.HttpStatus;

/**
 * A service's mapping from domain error codes to HTTP statuses. Declare one bean per service;
 * unmapped rule violations are reported as 422 Unprocessable Content.
 */
@FunctionalInterface
public interface ErrorStatusMapping {

  Map<String, HttpStatus> statuses();
}
