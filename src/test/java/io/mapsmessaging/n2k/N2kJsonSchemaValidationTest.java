/*
 *
 *  Copyright [ 2020 - 2024 ] Matthew Buckton
 *  Copyright [ 2024 - 2026 ] MapsMessaging B.V.
 *
 *  Licensed under the Apache License, Version 2.0 with the Commons Clause
 *  (the "License"); you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *      https://commonsclause.com/
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package io.mapsmessaging.n2k;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.mapsmessaging.n2k.codec.N2kMessageParser;
import io.mapsmessaging.n2k.compile.N2kCompiledField;
import io.mapsmessaging.n2k.compile.N2kCompiledMessage;
import io.mapsmessaging.n2k.compile.N2kCompiledRegistry;
import io.mapsmessaging.n2k.model.N2kFieldType;
import io.mapsmessaging.n2k.schema.N2kSchemaRegistry;
import java.util.*;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

class N2kJsonSchemaValidationTest extends BaseTest{

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
        if(!decoded.has(fieldId)){

        }
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
}
