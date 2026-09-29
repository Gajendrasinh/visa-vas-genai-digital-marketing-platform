package com.vasmarketing.customer.domain.merchant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.vasmarketing.customer.domain.customer.SpendCategory;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class MerchantTest {

  private static final Instant NOW = Instant.parse("2026-10-01T09:00:00Z");

  @ParameterizedTest
  @CsvSource({
    "3058, TRAVEL",
    "3501, TRAVEL",
    "4511, TRAVEL",
    "4722, TRAVEL",
    "7011, TRAVEL",
    "5812, DINING",
    "5814, DINING",
    "5411, GROCERY",
    "5541, FUEL",
    "5983, FUEL",
    "7832, ENTERTAINMENT",
    "5815, ONLINE",
    "5311, RETAIL",
    "5651, RETAIL",
    "5945, RETAIL",
    "8011, OTHER"
  })
  void mapsMccToSpendCategory(String mcc, SpendCategory expected) {
    assertThat(new MerchantCategoryCode(mcc).category()).isEqualTo(expected);
  }

  @Test
  void rejectsMalformedMcc() {
    assertThatThrownBy(() -> new MerchantCategoryCode("58A2"))
        .extracting("code")
        .isEqualTo(MerchantErrors.INVALID_MERCHANT);
  }

  @Test
  void lifecycleAndValidation() {
    Merchant merchant =
        Merchant.register("SkyWays Air", new MerchantCategoryCode("4511"), "US", "Denver", NOW);
    assertThat(merchant.category()).isEqualTo(SpendCategory.TRAVEL);

    merchant.update("SkyWays Airlines", new MerchantCategoryCode("4511"), null, NOW);
    assertThat(merchant.city()).isEmpty();
    merchant.deactivate(NOW);
    assertThat(merchant.active()).isFalse();
    assertThatThrownBy(() -> merchant.deactivate(NOW))
        .extracting("code")
        .isEqualTo(MerchantErrors.INVALID_TRANSITION);
    assertThatThrownBy(
            () -> Merchant.register("X", new MerchantCategoryCode("4511"), "usa", null, NOW))
        .extracting("code")
        .isEqualTo(MerchantErrors.INVALID_MERCHANT);
  }
}
