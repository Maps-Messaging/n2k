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

import com.google.gson.JsonObject;
import io.mapsmessaging.n2k.codec.N2kMessageParser;
import io.mapsmessaging.n2k.compile.N2kCompiledRegistry;
import io.mapsmessaging.n2k.compile.N2kCompiler;
import io.mapsmessaging.n2k.model.N2kMessageDefinition;
import io.mapsmessaging.n2k.parser.N2kXmlDialectParser;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;



class N2kCodecRoundTripTest {
  @Test
  void loadsDialectFromClasspath() throws Exception {
    N2kCompiledRegistry registry = buildRegistry();
    Assertions.assertNotNull(registry.getRequiredMessage(127245));
    Assertions.assertNotNull(registry.getRequiredMessage(127250));
  }

  @Test
  void pgn127245_rudder_roundTrip() throws Exception {
    N2kCompiledRegistry registry = buildRegistry();
    N2kMessageParser parser = new N2kMessageParser(registry);

    JsonObject decoded = new JsonObject();
    decoded.addProperty("rudderInstance", 1);
    decoded.addProperty("directionOrder", 3);
    decoded.addProperty("angleOrder", 0.1234);
    decoded.addProperty("position", -0.2500);

    JsonObject envelope = new JsonObject();
    envelope.addProperty("pgn", 127245);
    envelope.add("decoded", decoded);

    byte[] payload = parser.encodeFromJson(127245, envelope);
    JsonObject decodedBack = parser.decodeToJson(127245, payload);

    assertEquals(127245, decodedBack.get("pgn").getAsInt());

    JsonObject decodedFields = decodedBack.getAsJsonObject("decoded");
    assertEquals(1, decodedFields.get("rudderInstance").getAsInt());
    assertEquals(3, decodedFields.get("directionOrder").getAsInt());

    assertEquals(0.1234, decodedFields.get("angleOrder").getAsDouble(), 0.00005);
    assertEquals(-0.2500, decodedFields.get("position").getAsDouble(), 0.00005);
  }

  @Test
  void pgn127250_vesselHeading_roundTrip() throws Exception {
    N2kCompiledRegistry registry = buildRegistry();
    N2kMessageParser parser = new N2kMessageParser(registry);

    JsonObject decoded = new JsonObject();
    decoded.addProperty("sid", 9);
    decoded.addProperty("headingSensorReading", 1.2345);
    decoded.addProperty("deviation", -0.1000);
    decoded.addProperty("variation", 0.2000);
    decoded.addProperty("headingSensorReference", 2);

    JsonObject envelope = new JsonObject();
    envelope.addProperty("pgn", 127250);
    envelope.add("decoded", decoded);

    byte[] payload = parser.encodeFromJson(127250, envelope);
    JsonObject decodedBack = parser.decodeToJson(127250, payload);

    assertEquals(127250, decodedBack.get("pgn").getAsInt());

    JsonObject decodedFields = decodedBack.getAsJsonObject("decoded");
    assertEquals(9, decodedFields.get("sid").getAsInt());
    assertEquals(2, decodedFields.get("headingSensorReference").getAsInt());

    assertEquals(1.2345, decodedFields.get("headingSensorReading").getAsDouble(), 0.00005);
    assertEquals(-0.1000, decodedFields.get("deviation").getAsDouble(), 0.00005);
    assertEquals(0.2000, decodedFields.get("variation").getAsDouble(), 0.00005);
  }


  private static N2kCompiledRegistry buildRegistry() throws Exception {
    return N2kParserFactory.getN2kParser();
  }
}
