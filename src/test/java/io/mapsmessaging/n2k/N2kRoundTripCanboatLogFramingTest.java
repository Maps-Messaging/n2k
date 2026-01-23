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

import static io.mapsmessaging.n2k.BaseTest.buildRegistry;
import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonObject;
import io.mapsmessaging.n2k.codec.N2kMessageParser;
import io.mapsmessaging.n2k.compile.N2kCompiledRegistry;
import io.mapsmessaging.n2k.framing.FrameHandler;
import io.mapsmessaging.n2k.framing.FramePacker;
import io.mapsmessaging.n2k.framing.builder.CanFrame;
import io.mapsmessaging.n2k.framing.message.KnownMessage;
import io.mapsmessaging.n2k.framing.message.Message;
import io.mapsmessaging.n2k.framing.message.UnknownMessage;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.Test;

class N2kRoundTripCanboatLogFramingTest {
  @Test
  void roundTrip_canboatCsv_jsonToFramesToHandler_decodedMustMatch() throws Exception {
    N2kCompiledRegistry registry = buildRegistry();
    N2kMessageParser parser = new N2kMessageParser(registry);
    FramePacker framePacker = new FramePacker(parser);
    FrameHandler frameHandler = new FrameHandler(parser);

    String resourceName = "merrimac-actisense-serial-2011.raw";
    InputStream inputStream = getClass().getClassLoader().getResourceAsStream(resourceName);
    assertNotNull(inputStream, "Missing test resource on classpath: " + resourceName);

    int totalLines = 0;
    int processed = 0;
    int assembled = 0;

    Map<Integer, Integer> unknownPgnCounts = new TreeMap<>();
    Map<Integer, Integer> decodeFailureCounts = new TreeMap<>();
    Map<Integer, Integer> packFailureCounts = new TreeMap<>();
    long start = System.nanoTime();
    try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
      String line;
      while ((line = reader.readLine()) != null) {
        totalLines++;

        line = line.trim();
        if (line.isEmpty() || line.startsWith("#")) {
          continue;
        }

        CanboatRow row = parseCanboatCsvRow(line);
        if (row == null) {
          continue;
        }

        if (!supportsPgn(parser, row.pgn)) {
          unknownPgnCounts.merge(row.pgn, 1, Integer::sum);
          continue;
        }

        JsonObject decodedEnvelope1;
        try {
          decodedEnvelope1 = parser.decodeToJson(row.pgn, row.payloadBytes);
        } catch (Exception exception) {
          decodeFailureCounts.merge(row.pgn, 1, Integer::sum);
          continue;
        }

        if (decodedEnvelope1 == null) {
          decodeFailureCounts.merge(row.pgn, 1, Integer::sum);
          continue;
        }

        JsonObject decoded1 = decodedEnvelope1.getAsJsonObject("decoded");
        if (decoded1 == null) {
          decodeFailureCounts.merge(row.pgn, 1, Integer::sum);
          continue;
        }

        List<CanFrame> frames;
        try {
          frames = framePacker.pack(
              row.pgn,
              row.priority,
              row.source,
              row.destination,
              decodedEnvelope1
          );
        } catch (Exception exception) {
          packFailureCounts.merge(row.pgn, 1, Integer::sum);
          continue;
        }

        JsonObject decodedEnvelope2 = feedFramesUntilComplete(frameHandler, frames, totalLines, row.pgn);
        if (decodedEnvelope2 == null) {
          fail("Did not reassemble any complete message from packed frames. line=" + totalLines + " pgn=" + row.pgn);
        }

        assembled++;

        JsonObject decoded2 = decodedEnvelope2.getAsJsonObject("decoded");
        assertNotNull(decoded2, "Missing decoded object in second envelope. line=" + totalLines);

        assertSemanticallyEqual(row.pgn, decoded1, decoded2, totalLines);

        processed++;
      }
    }
    start = (System.nanoTime() - start)/1000000;
    double msTime = start;

    assertTrue(processed > 0, "No rows processed. lines=" + totalLines);
    assertTrue(assembled > 0, "No messages were ever reassembled from frames. lines=" + totalLines);

    System.err.println("Framing test processed " + processed + " rows; reassembled " + assembled + " messages; lines=" + totalLines);
    dumpCounts("Unknown PGNs", unknownPgnCounts, 30);
    dumpCounts("Decode failures", decodeFailureCounts, 30);
    dumpCounts("Pack failures", packFailureCounts, 30);
    System.err.println("Total time to parser "+totalLines+" "+start+"ms");
    System.err.println("Time per pgn "+(msTime/totalLines)+" ms");
  }

  private static boolean supportsPgn(N2kMessageParser parser, int pgn) {
    try {
      return parser.getRegistry().getRequiredMessage(pgn) != null;
    } catch (Exception exception) {
      return false;
    }
  }

  private static void dumpCounts(String title, Map<Integer, Integer> counts, int limit) {
    if (counts.isEmpty()) {
      return;
    }
    System.err.println(title + ": " + counts.size());
    int printed = 0;
    for (Map.Entry<Integer, Integer> entry : counts.entrySet()) {
      System.err.println("  pgn=" + entry.getKey() + " count=" + entry.getValue());
      printed++;
      if (printed >= limit) {
        System.err.println("  ... truncated");
        break;
      }
    }
  }

  private static JsonObject feedFramesUntilComplete(FrameHandler frameHandler, List<CanFrame> frames, int line, int pgn) {
    JsonObject decodedEnvelope = null;

    for (CanFrame frame : frames) {
      Optional<Message> message = frameHandler.onFrame(
          frame.getCanIdentifier(),
          true,
          frame.getDataLengthCode(),
          frame.getData()
      );

      if (message.isEmpty()) {
        continue;
      }

      Message finalMessage = message.get();

      if (finalMessage instanceof KnownMessage knownMessage) {
        decodedEnvelope = knownMessage.getDecoded();
        continue;
      }

      if (finalMessage instanceof UnknownMessage unknownMessage) {
        fail("Unexpected unknown from handler. line=" + line +
            " pgn=" + pgn +
            " reason=" + unknownMessage.getReason() +
            " detail=" + unknownMessage.getDetail());
      }

      fail("Unexpected Message type from handler. line=" + line + " pgn=" + pgn + " type=" + finalMessage.getClass().getName());
    }

    return decodedEnvelope;
  }

  private static CanboatRow parseCanboatCsvRow(String line) {
    String[] parts = line.split(",");
    if (parts.length < 7) {
      return null;
    }

    int priority = parseInt(parts[1]);
    int pgn = parseInt(parts[2]);
    int source = parseInt(parts[3]);
    int destination = parseInt(parts[4]);
    int length = parseInt(parts[5]);

    int expectedParts = 6 + length;
    if (parts.length < expectedParts) {
      throw new IllegalArgumentException(
          "Line has fewer payload bytes than declared length. " +
              "pgn=" + pgn + " declaredLen=" + length + " parts=" + parts.length + " expected>=" + expectedParts +
              " line=" + line
      );
    }

    byte[] payload = new byte[length];
    for (int i = 0; i < length; i++) {
      payload[i] = (byte) Integer.parseInt(parts[6 + i].trim(), 16);
    }

    return new CanboatRow(priority, pgn, source, destination, length, payload);
  }

  private static int parseInt(String s) {
    return Integer.parseInt(s.trim());
  }

  private static final class CanboatRow {
    private final int priority;
    private final int pgn;
    private final int source;
    private final int destination;
    private final int length;
    private final byte[] payloadBytes;

    private CanboatRow(int priority, int pgn, int source, int destination, int length, byte[] payloadBytes) {
      this.priority = priority;
      this.pgn = pgn;
      this.source = source;
      this.destination = destination;
      this.length = length;
      this.payloadBytes = payloadBytes;
    }
  }

  private static void assertSemanticallyEqual(int pgn, JsonObject a, JsonObject b, int line) {
    for (String key : a.keySet()) {
      if (!b.has(key)) {
        fail("Missing key in re-decoded object. pgn=" + pgn + " line=" + line + " key=" + key);
      }

      if (isUnstableNumericField(pgn, key)) {
        double av = a.get(key).getAsDouble();
        double bv = b.get(key).getAsDouble();
        double eps = epsilonFor(pgn, key);
        if (Double.isFinite(av) && Double.isFinite(bv)) {
          if (Math.abs(av - bv) > eps) {
            fail("Unstable field drift too large. pgn=" + pgn + " line=" + line +
                " key=" + key + " a=" + av + " b=" + bv + " eps=" + eps);
          }
        }
        continue;
      }

      if (!a.get(key).equals(b.get(key))) {
        fail("Field mismatch. pgn=" + pgn + " line=" + line +
            " key=" + key + " a=" + a.get(key) + " b=" + b.get(key));
      }
    }
  }

  private static boolean isUnstableNumericField(int pgn, String key) {
    if (pgn == 129029) {
      return "latitude".equals(key) || "longitude".equals(key) || "altitude".equals(key);
    }
    return false;
  }

  private static double epsilonFor(int pgn, String key) {
    if (pgn == 129029) {
      if ("latitude".equals(key) || "longitude".equals(key)) {
        return 1e-10;
      }
      if ("altitude".equals(key)) {
        return 1e-5;
      }
    }
    return 0.0;
  }
}