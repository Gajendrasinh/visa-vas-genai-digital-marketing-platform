package com.vasmarketing.events;

import static org.assertj.core.api.Assertions.assertThat;

import com.vasmarketing.events.campaign.CampaignLifecycleEvent;
import com.vasmarketing.events.merchant.MerchantReference;
import com.vasmarketing.events.offer.OfferLifecycleEvent;
import java.util.List;
import org.apache.avro.Schema;
import org.apache.avro.SchemaCompatibility;
import org.apache.avro.SchemaCompatibility.SchemaCompatibilityType;
import org.junit.jupiter.api.Test;

/**
 * Local guard for schema evolution rules; the registry check (profile schema-registry) is the
 * authoritative BACKWARD_TRANSITIVE gate against already-published versions.
 */
class SchemaEvolutionTest {

  @Test
  void everyTopLevelEventEmbedsMetadata() {
    for (Schema schema :
        List.of(
            CampaignLifecycleEvent.getClassSchema(),
            OfferLifecycleEvent.getClassSchema(),
            MerchantReference.getClassSchema())) {
      assertThat(schema.getField("metadata").schema().getFullName())
          .isEqualTo("com.vasmarketing.events.EventMetadata");
    }
  }

  @Test
  void enumsHaveDefaultsSoNewSymbolsStayReadableByOldConsumers() {
    assertThat(
            CampaignLifecycleEvent.getClassSchema()
                .getField("transition")
                .schema()
                .getEnumDefault())
        .isEqualTo("UNKNOWN");
    assertThat(OfferLifecycleEvent.getClassSchema().getField("change").schema().getEnumDefault())
        .isEqualTo("UNKNOWN");
  }

  @Test
  void addingAnOptionalFieldIsBackwardCompatible() {
    Schema current = CampaignLifecycleEvent.getClassSchema();
    Schema evolved =
        new Schema.Parser()
            .parse(
                current
                    .toString()
                    .replace(
                        "\"name\":\"reason\"",
                        "\"name\":\"budgetCurrency\",\"type\":[\"null\",\"string\"],\"default\":null},{\"name\":\"reason\""));

    assertThat(SchemaCompatibility.checkReaderWriterCompatibility(evolved, current).getType())
        .as("new reader can read old data")
        .isEqualTo(SchemaCompatibilityType.COMPATIBLE);
  }

  @Test
  void addingARequiredFieldWithoutDefaultCannotReadOldData() {
    Schema current = MerchantReference.getClassSchema();
    Schema withNewRequiredField =
        new Schema.Parser()
            .parse(
                current
                    .toString()
                    .replace(
                        "\"name\":\"active\"",
                        "\"name\":\"tier\",\"type\":\"string\"},{\"name\":\"active\""));

    assertThat(
            SchemaCompatibility.checkReaderWriterCompatibility(withNewRequiredField, current)
                .getType())
        .isEqualTo(SchemaCompatibilityType.INCOMPATIBLE);
  }
}
