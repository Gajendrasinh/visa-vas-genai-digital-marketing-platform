package com.vasmarketing.platform.types;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

class PaginationAndPreconditionTest {

  @Test
  void overfetchedRowSignalsNextPage() {
    List<UUID> rows = List.of(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());

    CursorPage<UUID> page = CursorPage.fromOverfetch(rows, 2, Function.identity());

    assertThat(page.items()).containsExactly(rows.get(0), rows.get(1));
    assertThat(page.nextCursor().map(CursorPage::decode)).contains(rows.get(1));
    assertThat(CursorPage.fromOverfetch(rows, 3, Function.identity()).nextCursor()).isEmpty();
    assertThat(page.map(UUID::toString).items()).hasSize(2);
  }

  @Test
  void validatesCursorAndLimit() {
    assertThatThrownBy(() -> CursorPage.decode("%%not-base64%%"))
        .extracting("code")
        .isEqualTo("INVALID_CURSOR");
    assertThatThrownBy(() -> CursorPage.limit(0)).extracting("code").isEqualTo("INVALID_PAGE_SIZE");
    assertThatThrownBy(() -> CursorPage.limit(101))
        .extracting("code")
        .isEqualTo("INVALID_PAGE_SIZE");
    assertThat(CursorPage.limit(100)).isEqualTo(100);
  }

  @Test
  void ifMatchComparesVersions() {
    Precondition.ifMatch(3).check(Optional.of(3L));
    Precondition.none().check(Optional.empty());

    assertThatThrownBy(() -> Precondition.ifMatch(3).check(Optional.of(4L)))
        .isInstanceOf(Precondition.PreconditionFailed.class)
        .hasMessageContaining("version 4");
    assertThatThrownBy(() -> Precondition.ifMatch(3).check(Optional.empty()))
        .isInstanceOf(Precondition.PreconditionFailed.class);
  }
}
