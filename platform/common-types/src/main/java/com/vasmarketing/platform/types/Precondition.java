package com.vasmarketing.platform.types;

import java.util.Optional;

/**
 * Optimistic-concurrency precondition from an HTTP {@code If-Match} header. A mutation carrying a
 * precondition applies only if the aggregate is still at the expected version.
 */
public sealed interface Precondition {

  /** Throws {@link PreconditionFailed} if the current version does not satisfy the precondition. */
  void check(Optional<Long> currentVersion);

  static Precondition none() {
    return None.INSTANCE;
  }

  static Precondition ifMatch(long expectedVersion) {
    return new IfMatch(expectedVersion);
  }

  /** No precondition: the mutation applies to whatever version is current. */
  enum None implements Precondition {
    INSTANCE;

    @Override
    public void check(Optional<Long> currentVersion) {
      // Unconditional by definition.
    }
  }

  record IfMatch(long expectedVersion) implements Precondition {
    @Override
    public void check(Optional<Long> currentVersion) {
      if (currentVersion.isEmpty() || currentVersion.get() != expectedVersion) {
        throw new PreconditionFailed(expectedVersion, currentVersion.orElse(null));
      }
    }
  }

  /** The resource changed since the client last read it. */
  final class PreconditionFailed extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public PreconditionFailed(long expected, Long actual) {
      super("resource is at version " + actual + ", not the expected " + expected);
    }
  }
}
