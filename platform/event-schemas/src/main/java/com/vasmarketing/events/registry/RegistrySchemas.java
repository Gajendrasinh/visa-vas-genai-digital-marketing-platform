package com.vasmarketing.events.registry;

import com.vasmarketing.events.campaign.CampaignLifecycleEvent;
import com.vasmarketing.events.merchant.MerchantReference;
import com.vasmarketing.events.offer.OfferLifecycleEvent;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.apache.avro.Schema;

/**
 * The value schema registered per topic subject ({@code <topic>-value}), exactly as producers'
 * serializers register it (nested records inlined). The build exports these files so the Schema
 * Registry compatibility gate checks the same schemas services will use.
 */
public final class RegistrySchemas {

  public static final Map<String, Schema> BY_TOPIC =
      Map.of(
          "campaign.events", CampaignLifecycleEvent.getClassSchema(),
          "offer.events", OfferLifecycleEvent.getClassSchema(),
          "merchant.reference", MerchantReference.getClassSchema());

  private RegistrySchemas() {}

  /** Writes {@code <topic>-value.avsc} files into the given directory. */
  public static void main(String[] args) throws IOException {
    Path dir = Path.of(args[0]);
    Files.createDirectories(dir);
    for (Map.Entry<String, Schema> entry : BY_TOPIC.entrySet()) {
      Files.writeString(
          dir.resolve(entry.getKey() + "-value.avsc"),
          entry.getValue().toString(true),
          StandardCharsets.UTF_8);
    }
  }
}
