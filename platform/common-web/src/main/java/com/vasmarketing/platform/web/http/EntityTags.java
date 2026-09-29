package com.vasmarketing.platform.web.http;

import com.vasmarketing.platform.types.Precondition;
import com.vasmarketing.platform.web.error.PlatformException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;

/**
 * Strong ETags derived from aggregate versions, and parsing of {@code If-Match} into a {@link
 * Precondition}. Weak validators are rejected: they cannot guard a write.
 */
public final class EntityTags {

  private static final Pattern STRONG_VERSION_TAG = Pattern.compile("^\"(\\d{1,18})\"$");

  private EntityTags() {}

  public static String of(long version) {
    return "\"" + version + "\"";
  }

  /** For writes that must not proceed without a version check (PUT). */
  public static Precondition required(String ifMatch) {
    if (ifMatch == null || ifMatch.isBlank()) {
      throw new PreconditionRequired();
    }
    return parse(ifMatch);
  }

  /** For state transitions where the precondition is recommended but not mandatory. */
  public static Precondition optional(String ifMatch) {
    return ifMatch == null || ifMatch.isBlank() ? Precondition.none() : parse(ifMatch);
  }

  private static Precondition parse(String ifMatch) {
    String value = ifMatch.trim();
    if ("*".equals(value)) {
      return Precondition.none();
    }
    Matcher matcher = STRONG_VERSION_TAG.matcher(value);
    if (!matcher.matches()) {
      throw new InvalidIfMatch();
    }
    return Precondition.ifMatch(Long.parseLong(matcher.group(1)));
  }

  /** 428: the client must send If-Match so lost updates are impossible. */
  static final class PreconditionRequired extends PlatformException {
    private static final long serialVersionUID = 1L;

    PreconditionRequired() {
      super(
          HttpStatus.PRECONDITION_REQUIRED,
          "PRECONDITION_REQUIRED",
          "If-Match required",
          "Send If-Match with the ETag from your last read of this resource.");
    }
  }

  static final class InvalidIfMatch extends PlatformException {
    private static final long serialVersionUID = 1L;

    InvalidIfMatch() {
      super(
          HttpStatus.BAD_REQUEST,
          "INVALID_IF_MATCH",
          "Invalid If-Match",
          "If-Match must be a single strong ETag such as \"3\".");
    }
  }
}
