package io.mapsmessaging.n2k;

import com.google.gson.JsonObject;
import io.mapsmessaging.n2k.codec.N2kMessageParser;
import io.mapsmessaging.n2k.compile.N2kCompiledMessage;
import io.mapsmessaging.n2k.compile.N2kCompiledRegistry;
import io.mapsmessaging.n2k.compile.N2kCompiler;
import io.mapsmessaging.n2k.model.N2kMessageDefinition;
import io.mapsmessaging.n2k.parser.N2kXmlDialectParser;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class N2kRoundTripCanboatLogTest {

  @Test
  void roundTrip_canboatCsv_payloadBytesMustMatch() throws Exception {
    // Adjust wiring to your actual constructors/singletons.
    N2kMessageParser parser = buildParser();

    String resourceName = "merrimac-actisense-serial-2011.raw";
    InputStream inputStream = getClass().getClassLoader().getResourceAsStream(resourceName);
    assertNotNull(inputStream, "Missing test resource on classpath: " + resourceName);

    int totalLines = 0;
    int processed = 0;

    try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
      String line;
      while ((line = reader.readLine()) != null) {
        totalLines++;

        line = line.trim();
        if (line.isEmpty()) {
          continue;
        }
        if (line.startsWith("#")) {
          continue;
        }

        CanboatRow row = parseCanboatCsvRow(line);
        if (row == null) {
          continue;
        }

        N2kCompiledMessage compiledMessage = parser.getRegistry().getRequiredMessage(row.pgn);
        if(compiledMessage == null){
          System.err.println("Unknown pgn::"+row.pgn);
          continue;
        }

        // a) bytes -> json
        JsonObject decodedEnvelope = parser.decodeToJson(row.pgn, row.payloadBytes);
        System.err.println("processing "+row.pgn);
        assertNotNull(decodedEnvelope, "decodeToJson returned null. line=" + totalLines);

        // b) json -> bytes
        byte[] reencoded = parser.encodeFromJson(row.pgn, decodedEnvelope);
        assertNotNull(reencoded, "encodeFromJson returned null. line=" + totalLines);
        int expectedLength = reencoded.length;

        byte[] sourceTrimmed = Arrays.copyOf(row.payloadBytes, expectedLength);

        // c) compare
        if (!Arrays.equals(sourceTrimmed, reencoded)) {
          String message =
              "N2K round-trip mismatch\n" +
                  "line=" + totalLines + "\n" +
                  "pgn=" + row.pgn + "\n" +
                  "src=" + row.source + " dst=" + row.destination + " prio=" + row.priority + "\n" +
                  "len=" + row.length + "\n" +
                  "original=" + toHex(row.payloadBytes) + "\n" +
                  "encoded =" + toHex(reencoded) + "\n" +
                  "decodedEnvelope=" + decodedEnvelope;
          fail(message);
        }

        processed++;
      }
    }

    assertTrue(processed > 0, "No rows processed. lines=" + totalLines);
  }

  private static CanboatRow parseCanboatCsvRow(String line) {
    // Format: timestamp,priority,pgn,src,dst,len, <len hex bytes...>
    // Example:
    // 2022-09-10T12:07:00.220Z,6,129794,23,255,76,05,80,26,...

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

  private static String toHex(byte[] bytes) {
    StringBuilder builder = new StringBuilder(bytes.length * 3);
    for (int i = 0; i < bytes.length; i++) {
      int value = bytes[i] & 0xFF;
      if (i > 0) {
        builder.append(' ');
      }
      builder.append(Character.forDigit((value >>> 4) & 0xF, 16));
      builder.append(Character.forDigit(value & 0xF, 16));
    }
    return builder.toString();
  }

  private static N2kMessageParser buildParser() throws Exception {
    List<N2kMessageDefinition> defs = N2kXmlDialectParser.parseFromClasspath("n2k/NMEA_database_1_300.xml");
    N2kCompiledRegistry registry = new N2kCompiledRegistry(N2kCompiler.compile(defs).getMessagesByPgn());
    return  new N2kMessageParser(registry);
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
}

