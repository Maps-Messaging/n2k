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
  private static volatile N2kCompiledRegistry registry;

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
    if (registry == null) {
      synchronized (N2kCodecRoundTripTest.class) {
        if (registry == null) {
          List<N2kMessageDefinition> messageDefinitions =
              N2kXmlDialectParser.parseFromClasspath("n2k/NMEA_database_1_300.xml");
          registry = N2kCompiler.compile(messageDefinitions);
        }
      }
    }
    return registry;
  }
}
