/*
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
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

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

class N2kRoundTripAllPgnsTest extends BaseTest{


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


}
