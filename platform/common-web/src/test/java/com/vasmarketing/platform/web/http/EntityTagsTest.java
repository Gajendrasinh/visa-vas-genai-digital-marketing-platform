package com.vasmarketing.platform.web.http;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.vasmarketing.platform.types.Precondition;
import com.vasmarketing.platform.web.error.PlatformException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;

class EntityTagsTest {

  @Test
  void formatsAndParsesStrongVersionTags() {
    assertThat(EntityTags.of(7)).isEqualTo("\"7\"");
    assertThat(EntityTags.required("\"7\"")).isEqualTo(Precondition.ifMatch(7));
    assertThat(EntityTags.optional(" \"7\" ")).isEqualTo(Precondition.ifMatch(7));
    assertThat(EntityTags.required("*")).isEqualTo(Precondition.none());
    assertThat(EntityTags.optional(null)).isEqualTo(Precondition.none());
  }

  @Test
  void missingIfMatchOnMandatoryWritesIs428() {
    assertThatThrownBy(() -> EntityTags.required(null))
        .isInstanceOfSatisfying(
            PlatformException.class,
            e -> assertThat(e.status()).isEqualTo(HttpStatus.PRECONDITION_REQUIRED));
  }

  @ParameterizedTest
  @ValueSource(strings = {"W/\"7\"", "7", "\"abc\"", "\"1\", \"2\""})
  void rejectsWeakOrMalformedTags(String header) {
    assertThatThrownBy(() -> EntityTags.optional(header))
        .isInstanceOfSatisfying(
            PlatformException.class, e -> assertThat(e.errorCode()).isEqualTo("INVALID_IF_MATCH"));
  }
}
