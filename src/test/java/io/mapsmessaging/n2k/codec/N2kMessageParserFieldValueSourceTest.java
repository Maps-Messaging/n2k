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

package io.mapsmessaging.n2k.codec;

import com.google.gson.JsonObject;
import io.mapsmessaging.n2k.N2kParserFactory;
import io.mapsmessaging.n2k.compile.N2kCompiledRegistry;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class N2kMessageParserFieldValueSourceTest {

  @Test
  void shouldEncodeFromFieldValueSourceSameAsJsonForPgn127250() throws Exception {
    int pgn = 127250;

    N2kCompiledRegistry registry = buildRegistry();
    N2kMessageParser parser = new N2kMessageParser(registry);

    JsonObject envelope = new JsonObject();
    JsonObject decoded = new JsonObject();
    envelope.addProperty("pgn", pgn);
    envelope.add("decoded", decoded);

    decoded.addProperty("sid", 9L);
    decoded.addProperty("headingSensorReading", 1.2345d);
    decoded.addProperty("deviation", -0.1d);
    decoded.addProperty("variation", 0.2d);
    decoded.addProperty("headingSensorReference", 2L);

    FieldValueSource source = new MapFieldValueSource()
        .withLong("sid", 9L)
        .withDouble("headingSensorReading", 1.2345d)
        .withDouble("deviation", -0.1d)
        .withDouble("variation", 0.2d)
        .withLong("headingSensorReference", 2L);

    byte[] jsonPayload = parser.encodeFromJson(pgn, envelope);
    byte[] sourcePayload = parser.encodeFromSource(pgn, source);

    assertNotNull(jsonPayload);
    assertNotNull(sourcePayload);
    assertTrue(jsonPayload.length > 0);
    assertArrayEquals(jsonPayload, sourcePayload);
  }

  private static N2kCompiledRegistry buildRegistry() throws Exception {
    return N2kParserFactory.getN2kParser();
  }

  private static final class MapFieldValueSource implements FieldValueSource {

    private final Map<String, Long> longValues;
    private final Map<String, Double> doubleValues;
    private final Map<String, String> stringValues;

    private MapFieldValueSource() {
      this.longValues = new HashMap<>();
      this.doubleValues = new HashMap<>();
      this.stringValues = new HashMap<>();
    }

    private MapFieldValueSource withLong(String fieldId, Long value) {
      longValues.put(fieldId, value);
      return this;
    }

    private MapFieldValueSource withDouble(String fieldId, Double value) {
      doubleValues.put(fieldId, value);
      return this;
    }

    private MapFieldValueSource withString(String fieldId, String value) {
      stringValues.put(fieldId, value);
      return this;
    }

    @Override
    public boolean has(String fieldId) {
      return longValues.containsKey(fieldId)
          || doubleValues.containsKey(fieldId)
          || stringValues.containsKey(fieldId);
    }

    @Override
    public Long getLong(String fieldId) {
      return longValues.get(fieldId);
    }

    @Override
    public Double getDouble(String fieldId) {
      return doubleValues.get(fieldId);
    }

    @Override
    public String getString(String fieldId) {
      return stringValues.get(fieldId);
    }
  }
}