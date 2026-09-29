package com.vasmarketing.platform.types;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

/**
 * One page of a keyset-paginated listing ordered by a time-ordered UUID (UUIDv7). Keyset paging
 * stays fast on deep pages and is stable under concurrent inserts, unlike OFFSET.
 */
public record CursorPage<T>(List<T> items, Optional<String> nextCursor) {

  public static final int MAX_LIMIT = 100;

  public CursorPage {
    items = List.copyOf(items);
    Objects.requireNonNull(nextCursor, "nextCursor");
  }

  /**
   * Builds a page from a query that fetched {@code limit + 1} rows: the extra row only signals that
   * another page exists.
   */
  public static <T> CursorPage<T> fromOverfetch(List<T> rows, int limit, Function<T, UUID> idOf) {
    if (rows.size() <= limit) {
      return new CursorPage<>(rows, Optional.empty());
    }
    List<T> page = rows.subList(0, limit);
    return new CursorPage<>(page, Optional.of(encode(idOf.apply(page.getLast()))));
  }

  public <R> CursorPage<R> map(Function<T, R> mapper) {
    return new CursorPage<>(items.stream().map(mapper).toList(), nextCursor);
  }

  public static String encode(UUID lastId) {
    return Base64.getUrlEncoder()
        .withoutPadding()
        .encodeToString(lastId.toString().getBytes(StandardCharsets.US_ASCII));
  }

  /** Decodes a client-supplied cursor; malformed cursors are a client error. */
  public static UUID decode(String cursor) {
    try {
      return UUID.fromString(
          new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.US_ASCII));
    } catch (IllegalArgumentException e) {
      throw new DomainRuleViolation("INVALID_CURSOR", "cursor is malformed");
    }
  }

  /** Validated page size. */
  public static int limit(int requested) {
    if (requested < 1 || requested > MAX_LIMIT) {
      throw new DomainRuleViolation("INVALID_PAGE_SIZE", "limit must be 1-" + MAX_LIMIT);
    }
    return requested;
  }
}
