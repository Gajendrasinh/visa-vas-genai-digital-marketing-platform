package com.vasmarketing.platform.kafka;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import org.apache.avro.io.BinaryEncoder;
import org.apache.avro.io.DecoderFactory;
import org.apache.avro.io.EncoderFactory;
import org.apache.avro.specific.SpecificData;
import org.apache.avro.specific.SpecificDatumReader;
import org.apache.avro.specific.SpecificDatumWriter;
import org.apache.avro.specific.SpecificRecord;

/**
 * Registry-free Avro binary encoding for outbox storage; the registry is only needed to publish.
 */
final class AvroBinary {

  /** Only generated event classes may be materialized from stored class names. */
  static final String EVENT_PACKAGE = "com.vasmarketing.events.";

  private AvroBinary() {}

  static byte[] encode(SpecificRecord record) {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    BinaryEncoder encoder = EncoderFactory.get().binaryEncoder(out, null);
    try {
      new SpecificDatumWriter<SpecificRecord>(record.getSchema()).write(record, encoder);
      encoder.flush();
    } catch (IOException e) {
      throw new UncheckedIOException("Avro encoding failed", e);
    }
    return out.toByteArray();
  }

  static SpecificRecord decode(String className, byte[] payload) {
    if (!className.startsWith(EVENT_PACKAGE)) {
      throw new IllegalStateException("refusing to decode non-event class " + className);
    }
    try {
      Class<? extends SpecificRecord> type =
          Class.forName(className).asSubclass(SpecificRecord.class);
      SpecificData data = SpecificData.getForClass(type);
      SpecificDatumReader<SpecificRecord> reader =
          new SpecificDatumReader<>(data.getSchema(type), data.getSchema(type), data);
      return reader.read(null, DecoderFactory.get().binaryDecoder(payload, null));
    } catch (ClassNotFoundException | ClassCastException e) {
      throw new IllegalStateException("unknown event class " + className, e);
    } catch (IOException e) {
      throw new UncheckedIOException("Avro decoding failed", e);
    }
  }
}
