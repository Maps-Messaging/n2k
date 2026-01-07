package io.mapsmessaging.n2k;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.mapsmessaging.n2k.codec.N2kMessageParser;
import io.mapsmessaging.n2k.compile.N2kCompiledField;
import io.mapsmessaging.n2k.compile.N2kCompiledMessage;
import io.mapsmessaging.n2k.compile.N2kCompiledRegistry;
import io.mapsmessaging.n2k.compile.N2kCompiler;
import io.mapsmessaging.n2k.model.N2kFieldType;
import io.mapsmessaging.n2k.model.N2kMessageDefinition;
import io.mapsmessaging.n2k.parser.N2kXmlDialectParser;
import io.mapsmessaging.n2k.schema.N2kSchemaRegistry;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class N2kRoundTripAllPgnsTest {

  private static final String DIALECT_RESOURCE_PATH = "n2k/NMEA_database_1_300.xml";
  private static final long BASE_SEED = 0x6b8b4567L;

  @TestFactory
  Stream<DynamicTest> allCompiledPgns_roundTrip_fixedWidthNumericFields() throws Exception {
    N2kCompiledRegistry registry = buildRegistry();
    N2kMessageParser parser = new N2kMessageParser(registry);

    List<N2kCompiledMessage> messages = new ArrayList<>(registry.getMessagesByPgn().values());
    messages.sort(Comparator.comparingInt(N2kCompiledMessage::getPgn));

    return messages.stream()
        .map(msg -> DynamicTest.dynamicTest(
            msg.getPgn() + " " + (msg.getId() == null ? "" : msg.getId()),
            () -> roundTripMessage(parser, msg)
        ));
  }

  @TestFactory
  Stream<DynamicTest> allCompiledPgns_decodeJson_conformsToGeneratedSchema() throws Exception {
    N2kCompiledRegistry registry = buildRegistry();
    N2kMessageParser parser = new N2kMessageParser(registry);
    N2kSchemaRegistry schemaRegistry = new N2kSchemaRegistry(registry);

    List<N2kCompiledMessage> messages = new ArrayList<>(registry.getMessagesByPgn().values());
    messages.sort(Comparator.comparingInt(N2kCompiledMessage::getPgn));

    return messages.stream()
        .map(msg -> DynamicTest.dynamicTest(
            msg.getPgn() + " " + (msg.getId() == null ? "" : msg.getId()),
            () -> schemaValidateDecodedEnvelope(parser, schemaRegistry, msg)
        ));
  }

// io.mapsmessaging.n2k.N2kRoundTripAllPgnsTest
// Replace schemaValidateDecodedEnvelope(...) with this version.

  private static void schemaValidateDecodedEnvelope(
      N2kMessageParser parser,
      N2kSchemaRegistry schemaRegistry,
      N2kCompiledMessage msg
  ) {
    JsonObject decoded = new JsonObject();
    Random random = new Random(BASE_SEED ^ (long) msg.getPgn());

    for (N2kCompiledField field : msg.getFields()) {
      if (field.isReserved()) {
        continue;
      }

      N2kFieldType type = field.getFieldType();
      if (type != N2kFieldType.NUMBER && type != N2kFieldType.LOOKUP && type != N2kFieldType.FLOAT) {
        continue;
      }

      String id = field.getId();
      if (id == null || id.isBlank()) {
        continue;
      }

      long rawValue = randomRawValue(field, random);
      long clampedRawValue = clampRawValueToSchemaRange(field, rawValue);

      if (type == N2kFieldType.LOOKUP) {
        decoded.addProperty(id, (int) (clampedRawValue & field.getMask()));
      }
      else {
        double value = clampedRawValue * field.getResolution() + field.getOffset();
        decoded.addProperty(id, value);
      }
    }

    JsonObject envelope = new JsonObject();
    envelope.addProperty("pgn", msg.getPgn());
    envelope.add("decoded", decoded);

    byte[] payload = parser.encodeFromJson(msg.getPgn(), envelope);
    assertNotNull(payload);

    JsonObject decodedBackEnvelope = parser.decodeToJson(msg.getPgn(), payload);
    assertNotNull(decodedBackEnvelope);

    JsonObject schema = schemaRegistry.getSchema(msg.getPgn());
    validateEnvelopeAgainstSchema(schema, decodedBackEnvelope, msg.getPgn());
  }


  private static long clampRawValueToSchemaRange(N2kCompiledField field, long rawValue) {
    long clamped = clampRawValue(field, rawValue);

    Double min = field.getRangeMin();
    Double max = field.getRangeMax();

    if (min == null && max == null) {
      return clamped;
    }

    double resolution = field.getResolution();
    if (resolution <= 0.0) {
      return clamped;
    }

    double offset = field.getOffset();

    long minRaw = Long.MIN_VALUE;
    long maxRaw = Long.MAX_VALUE;

    if (min != null) {
      minRaw = Math.round((min - offset) / resolution);
    }
    if (max != null) {
      maxRaw = Math.round((max - offset) / resolution);
    }

    if (minRaw > maxRaw) {
      return clamped;
    }

    if (clamped < minRaw) {
      return minRaw;
    }
    if (clamped > maxRaw) {
      return maxRaw;
    }

    return clamped;
  }




  private static void validateEnvelopeAgainstSchema(JsonObject schema, JsonObject envelope, int pgn) {
    assertNotNull(schema);
    assertNotNull(envelope);

    assertTrue(envelope.has("pgn"), "Missing pgn for PGN=" + pgn);
    assertEquals(pgn, envelope.get("pgn").getAsInt(), "pgn mismatch for PGN=" + pgn);

    assertTrue(envelope.has("decoded"), "Missing decoded for PGN=" + pgn);
    JsonObject decoded = envelope.getAsJsonObject("decoded");
    assertNotNull(decoded, "decoded is not an object for PGN=" + pgn);

    JsonObject schemaProperties = schema.getAsJsonObject("properties");
    assertNotNull(schemaProperties, "Schema missing properties for PGN=" + pgn);

    JsonObject decodedSchema = schemaProperties.getAsJsonObject("decoded");
    assertNotNull(decodedSchema, "Schema missing decoded for PGN=" + pgn);

    JsonObject decodedSchemaProperties = decodedSchema.getAsJsonObject("properties");
    assertNotNull(decodedSchemaProperties, "Schema decoded missing properties for PGN=" + pgn);

    boolean additionalPropertiesAllowed = true;
    if (decodedSchema.has("additionalProperties")) {
      additionalPropertiesAllowed = decodedSchema.get("additionalProperties").getAsBoolean();
    }

    // Enforce required decoded fields
    if (decodedSchema.has("required")) {
      JsonArray required = decodedSchema.getAsJsonArray("required");
      for (JsonElement req : required) {
        String fieldId = req.getAsString();
        assertTrue(decoded.has(fieldId), "Missing required decoded field '" + fieldId + "' for PGN=" + pgn);
      }
    }

    // Enforce schema for every decoded property
    for (Map.Entry<String, JsonElement> entry : decoded.entrySet()) {
      String fieldId = entry.getKey();
      JsonElement value = entry.getValue();

      JsonObject fieldSchema = decodedSchemaProperties.getAsJsonObject(fieldId);

      if (!additionalPropertiesAllowed) {
        assertNotNull(fieldSchema, "Unexpected decoded field '" + fieldId + "' for PGN=" + pgn);
      }
      if (fieldSchema == null) {
        continue;
      }

      String expectedType = fieldSchema.has("type") ? fieldSchema.get("type").getAsString() : null;
      if (expectedType == null) {
        continue;
      }

      if ("number".equals(expectedType)) {
        assertTrue(value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber(),
            "Field '" + fieldId + "' expected number for PGN=" + pgn);
      }
      else if ("integer".equals(expectedType)) {
        assertTrue(value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber(),
            "Field '" + fieldId + "' expected integer(number) for PGN=" + pgn);

        double d = value.getAsDouble();
        assertEquals(Math.rint(d), d, 0.0, "Field '" + fieldId + "' expected integer value for PGN=" + pgn);
      }
      else if ("string".equals(expectedType)) {
        assertTrue(value.isJsonPrimitive() && value.getAsJsonPrimitive().isString(),
            "Field '" + fieldId + "' expected string for PGN=" + pgn);
        continue;
      }

      double tolerance = 0.0;
      if (fieldSchema.has("multipleOf")) {
        tolerance = fieldSchema.get("multipleOf").getAsDouble() * 0.51;
      }

      if (fieldSchema.has("minimum")) {
        double min = fieldSchema.get("minimum").getAsDouble();
        if(value.getAsDouble() + tolerance < min){
          System.err.println("check");
        }
        assertTrue(value.getAsDouble() + tolerance >= min, "Field '" + fieldId + "' below minimum for PGN=" + pgn);
      }
      if (fieldSchema.has("maximum")) {
        double max = fieldSchema.get("maximum").getAsDouble();
        assertTrue(value.getAsDouble() - tolerance <= max, "Field '" + fieldId + "' above maximum for PGN=" + pgn);
      }
    }

    // Root strictness
    if (schema.has("additionalProperties") && !schema.get("additionalProperties").getAsBoolean()) {
      for (Map.Entry<String, JsonElement> entry : envelope.entrySet()) {
        String key = entry.getKey();
        if (!"pgn".equals(key) && !"decoded".equals(key)) {
          fail("Unexpected root property '" + key + "' for PGN=" + pgn);
        }
      }
    }
  }

  private static void roundTripMessage(N2kMessageParser parser, N2kCompiledMessage msg) {
    JsonObject decoded = new JsonObject();

    Random random = new Random(BASE_SEED ^ (long) msg.getPgn());

    for (N2kCompiledField field : msg.getFields()) {
      if (field.isReserved()) {
        continue;
      }

      N2kFieldType type = field.getFieldType();
      if (type != N2kFieldType.NUMBER && type != N2kFieldType.LOOKUP && type != N2kFieldType.FLOAT) {
        continue;
      }

      String id = field.getId();
      if (id == null || id.isBlank()) {
        continue;
      }

      long rawValue = randomRawValue(field, random);
      long clampedRawValue = clampRawValue(field, rawValue);

      double value = clampedRawValue * field.getResolution() + field.getOffset();

      if (type == N2kFieldType.LOOKUP) {
        decoded.addProperty(id, (int) (clampedRawValue & field.getMask()));
      }
      else {
        decoded.addProperty(id, value);
      }
    }

    JsonObject envelope = new JsonObject();
    envelope.addProperty("pgn", msg.getPgn());
    envelope.add("decoded", decoded);

    byte[] payload = parser.encodeFromJson(msg.getPgn(), envelope);
    assertNotNull(payload);

    JsonObject decodedBackEnvelope = parser.decodeToJson(msg.getPgn(), payload);
    assertNotNull(decodedBackEnvelope);

    assertEquals(msg.getPgn(), decodedBackEnvelope.get("pgn").getAsInt());

    JsonObject decodedBack = decodedBackEnvelope.getAsJsonObject("decoded");
    assertNotNull(decodedBack);

    for (Map.Entry<String, JsonElement> entry : decoded.entrySet()) {
      String fieldId = entry.getKey();
      JsonElement expectedJson = entry.getValue();

      JsonElement actualJson = decodedBack.get(fieldId);
      assertNotNull(actualJson, "Missing field after decode: " + fieldId + " PGN=" + msg.getPgn());

      N2kCompiledField field = fieldById(msg, fieldId);
      assertNotNull(field, "Field not found in compiled message: " + fieldId + " PGN=" + msg.getPgn());

      N2kFieldType type = field.getFieldType();
      if (type == N2kFieldType.LOOKUP) {
        assertEquals(expectedJson.getAsInt(), actualJson.getAsInt(), "LOOKUP mismatch for " + fieldId + " PGN=" + msg.getPgn());
      }
      else {
        double expected = expectedJson.getAsDouble();
        double actual = actualJson.getAsDouble();
        double tolerance = toleranceFor(field);
        assertEquals(expected, actual, tolerance, "NUMERIC mismatch for " + fieldId + " PGN=" + msg.getPgn());
      }
    }
  }

  private static long clampRawValue(N2kCompiledField field, long rawValue) {
    if (!field.isSigned()) {
      if (rawValue < 0L) {
        return 0L;
      }
      long max = field.getMask();
      if (max != 0L && rawValue > max) {
        return max;
      }
      return rawValue;
    }

    int bitLength = field.getBitLength();
    if (bitLength > 0 && bitLength < 64) {
      long min = -(1L << (bitLength - 1));
      long max = (1L << (bitLength - 1)) - 1L;

      if (rawValue < min) {
        return min;
      }
      if (rawValue > max) {
        return max;
      }
    }

    return rawValue;
  }

  private static double toleranceFor(N2kCompiledField field) {
    double resolution = field.getResolution();
    if (resolution <= 0.0) {
      return 0.0;
    }
    return Math.max(1e-12, resolution * 0.51);
  }

  private static N2kCompiledField fieldById(N2kCompiledMessage msg, String id) {
    for (N2kCompiledField f : msg.getFields()) {
      if (id.equals(f.getId())) {
        return f;
      }
    }
    return null;
  }
  private static long randomRawValue(N2kCompiledField field, Random random) {
    RawRange range = computeAllowedRawRange(field);

    if (!range.valid) {
      return 0L;
    }

    if (range.min == range.max) {
      return range.min;
    }

    long span = range.max - range.min;
    long offset = nextLongBounded(random, span + 1L);

    return range.min + offset;
  }

  private static RawRange computeAllowedRawRange(N2kCompiledField field) {
    int bitLength = field.getBitLength();
    if (bitLength <= 0) {
      return RawRange.invalid();
    }

    long bitMin;
    long bitMax;

    if (field.isSigned()) {
      if (bitLength >= 64) {
        bitMin = Long.MIN_VALUE;
        bitMax = Long.MAX_VALUE;
      }
      else {
        bitMin = -(1L << (bitLength - 1));
        bitMax = (1L << (bitLength - 1)) - 1L;
      }
    }
    else {
      bitMin = 0L;
      bitMax = field.getMask();
      if (bitMax <= 0L) {
        return RawRange.invalid();
      }
    }

    Double rangeMin = field.getRangeMin();
    Double rangeMax = field.getRangeMax();

    if (rangeMin == null && rangeMax == null) {
      return RawRange.of(bitMin, bitMax);
    }

    double resolution = field.getResolution();
    if (resolution <= 0.0) {
      return RawRange.of(bitMin, bitMax);
    }

    double offset = field.getOffset();

    long rawFromRangeMin = bitMin;
    long rawFromRangeMax = bitMax;

    if (rangeMin != null) {
      double unscaledMin = (rangeMin - offset) / resolution;
      rawFromRangeMin = (long) Math.ceil(unscaledMin - 1e-12);
    }

    if (rangeMax != null) {
      double unscaledMax = (rangeMax - offset) / resolution;
      rawFromRangeMax = (long) Math.floor(unscaledMax + 1e-12);
    }

    long min = Math.max(bitMin, rawFromRangeMin);
    long max = Math.min(bitMax, rawFromRangeMax);

    if (!field.isSigned()) {
      long mask = field.getMask();
      min &= mask;
      max &= mask;
    }

    if (min > max) {
      // Range and bit layout disagree; safest is clamp to bit range rather than inventing nonsense.
      return RawRange.of(bitMin, bitMax);
    }

    return RawRange.of(min, max);
  }

  private static final class RawRange {
    final boolean valid;
    final long min;
    final long max;

    private RawRange(boolean valid, long min, long max) {
      this.valid = valid;
      this.min = min;
      this.max = max;
    }

    static RawRange of(long min, long max) {
      return new RawRange(true, min, max);
    }

    static RawRange invalid() {
      return new RawRange(false, 0L, 0L);
    }
  }


  private static long nextLongBounded(Random random, long boundExclusive) {
    if (boundExclusive <= 0L) {
      return 0L;
    }

    long r = random.nextLong();
    long m = boundExclusive - 1L;

    if ((boundExclusive & m) == 0L) {
      return r & m;
    }

    long u = r >>> 1;
    while (u + m - (u % boundExclusive) < 0L) {
      u = (random.nextLong() >>> 1);
    }

    return u % boundExclusive;
  }

  private static N2kCompiledRegistry buildRegistry() throws Exception {
    List<N2kMessageDefinition> defs = N2kXmlDialectParser.parseFromClasspath(DIALECT_RESOURCE_PATH);
    return N2kCompiler.compile(defs);
  }
}
