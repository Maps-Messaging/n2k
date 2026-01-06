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

  // One thing I genuinely need from you: what is the actual classpath resource path of your official N2K XML file?
  // Replace this constant with it.
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
    int bitLength = field.getBitLength();
    if (bitLength <= 0) {
      return 0L;
    }

    if (field.isSigned()) {
      if (bitLength >= 64) {
        return random.nextLong();
      }

      long min = -(1L << (bitLength - 1));
      long max = (1L << (bitLength - 1)) - 1L;

      long span = max - min;
      long offset = nextLongBounded(random, span + 1L);

      return min + offset;
    }

    long mask = field.getMask();
    if (mask == 0L) {
      return 0L;
    }

    if (bitLength >= 64) {
      return random.nextLong();
    }

    return nextLongBounded(random, mask + 1L);
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
