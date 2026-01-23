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

import com.google.gson.JsonObject;
import io.mapsmessaging.n2k.codec.N2kMessageParser;
import io.mapsmessaging.n2k.compile.N2kCompiledField;
import io.mapsmessaging.n2k.compile.N2kCompiledMessage;
import io.mapsmessaging.n2k.compile.N2kCompiledRegistry;
import io.mapsmessaging.n2k.compile.N2kCompiler;
import io.mapsmessaging.n2k.model.N2kFieldDefinition;
import io.mapsmessaging.n2k.model.N2kFieldType;
import io.mapsmessaging.n2k.model.N2kMessageDefinition;
import io.mapsmessaging.n2k.parser.N2kXmlDialectParser;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.jupiter.api.Test;

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

    Map<Integer, List<byte[]>> unknown = new LinkedHashMap<>();
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
          List<byte[]> list = unknown.computeIfAbsent(row.pgn, k -> new ArrayList<>());
          list.add(row.payloadBytes);
          continue;
        }

        // a) bytes -> json
        JsonObject decodedEnvelope = parser.decodeToJson(row.pgn, row.payloadBytes);
        assertNotNull(decodedEnvelope, "decodeToJson returned null. line=" + totalLines);

        // b) json -> bytes
        byte[] reencoded = parser.encodeFromJson(row.pgn, decodedEnvelope);

        JsonObject decoded2Envelope = parser.decodeToJson(row.pgn, reencoded);
        assertNotNull(decoded2Envelope, "decodeToJson( reencoded ) returned null. line=" + totalLines);

        JsonObject decoded1 = decodedEnvelope.getAsJsonObject("decoded");
        JsonObject decoded2 = decoded2Envelope.getAsJsonObject("decoded");
        assertNotNull(decoded1, "Missing decoded object in first envelope. line=" + totalLines);
        assertNotNull(decoded2, "Missing decoded object in second envelope. line=" + totalLines);

// 1) semantic checks (preferred)
        assertSemanticallyEqual(row.pgn, decoded1, decoded2, totalLines);

// 2) byte checks (masked for fields that cannot be bit-perfect via double)
// 2) byte checks (masked for fields that cannot be bit-perfect via double)
        int size = Math.min(reencoded.length, row.payloadBytes.length);

        byte[] sourceTrimmed = Arrays.copyOf(row.payloadBytes, size);
        byte[] encodedTrimmed = Arrays.copyOf(reencoded, size);

        byte[] sourceMasked = Arrays.copyOf(sourceTrimmed, size);
        byte[] encodedMasked = Arrays.copyOf(encodedTrimmed, size);

// Apply mask for known unstable numeric fields (lat/lon/alt etc)
        maskUnstableFields(row.pgn, sourceMasked, encodedMasked);
        List<N2kFieldDefinition> fields = compiledMessage.getDefinitions();
        maskNonPayloadBits(sourceMasked, fields);
        maskNonPayloadBits(encodedMasked, fields);

// Compare masked arrays, with STRING_FIX padding tolerance
        if (!equalsWithStringFixPaddingTolerance(compiledMessage, sourceMasked, encodedMasked)) {
          String message =
              "N2K round-trip mismatch (masked)\n" +
                  "line=" + totalLines + "\n" +
                  "pgn=" + row.pgn + "\n" +
                  "src=" + row.source + " dst=" + row.destination + " prio=" + row.priority + "\n" +
                  "len=" + row.length + "\n" +
                  "original=" + toHex(sourceTrimmed) + "\n" +
                  "encoded =" + toHex(encodedTrimmed) + "\n" +
                  "maskedOriginal=" + toHex(sourceMasked) + "\n" +
                  "maskedEncoded =" + toHex(encodedMasked) + "\n" +
                  "decoded1=" + decodedEnvelope + "\n" +
                  "decoded2=" + decoded2Envelope;
          fail(message);
        }


        processed++;
      }
    }

    assertTrue(processed > 0, "No rows processed. lines=" + totalLines);
    System.err.println("Parsed and processed "+processed+" rows with "+unknown.size()+" of unknown PGN");
    for(Map.Entry<Integer, List<byte[]>> entry: unknown.entrySet()){
      System.err.println("pgn="+entry.getKey());
      int end = Math.min(10, entry.getValue().size());
      for(int x=0;x<end;x++){
        System.err.println("\t"+dump(entry.getValue().get(x)));
      }
    }
  }

  private String dump(byte[] line){
    StringBuilder sb = new StringBuilder();
    for(byte b:line){
      String t = Long.toHexString(b&0xff);
      if(t.length()<2){
        sb.append("0x0");
      }
      else{
        sb.append("0x");
      }
      sb.append(t).append(" ");
    }

    return sb.toString();
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

  private static void assertSemanticallyEqual(int pgn, JsonObject a, JsonObject b, int line) {
    // Default: all numeric values must match exactly unless listed as unstable.
    // LOOKUP values must match exactly always.

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

      // Exact compare for everything else (numbers/ints/lookup)
      if (!a.get(key).equals(b.get(key))) {
        fail("Field mismatch. pgn=" + pgn + " line=" + line +
            " key=" + key + " a=" + a.get(key) + " b=" + b.get(key));
      }
    }

  }

  private static boolean isUnstableNumericField(int pgn, String key) {
    // Add PGNs/fields as you encounter them.
    // For 129029 these three are the known precision wobbly ones.
    if (pgn == 129029) {
      return "latitude".equals(key) || "longitude".equals(key) || "altitude".equals(key);
    }
    return false;
  }

  private static double epsilonFor(int pgn, String key) {
    if (pgn == 129029) {
      if ("latitude".equals(key) || "longitude".equals(key)) {
        // degrees; allow tiny drift from int64<->double roundtrip
        return 1e-10;
      }
      if ("altitude".equals(key)) {
        // metres; int64 with 1e-6 resolution, allow a few micrometres
        return 1e-5;
      }
    }
    return 0.0;
  }

  private static void maskUnstableFields(int pgn, byte[] original, byte[] encoded) {
    // Mask byte ranges corresponding to unstable fields.
    // For 129029:
    // latitude:  bit 56  len 64 => bytes 7..14
    // longitude: bit 120 len 64 => bytes 15..22
    // altitude:  bit 184 len 64 => bytes 23..30
    if (pgn == 129029) {
      maskRange(original, encoded, 7, 8);
      maskRange(original, encoded, 15, 8);
      maskRange(original, encoded, 23, 8);
    }
  }

  private static void maskRange(byte[] a, byte[] b, int start, int length) {
    int end = Math.min(a.length, start + length);
    end = Math.min(end, b.length);
    for (int i = start; i < end; i++) {
      a[i] = 0;
      b[i] = 0;
    }
  }

  private static boolean equalsWithStringFixPaddingTolerance(
      N2kCompiledMessage message,
      byte[] original,
      byte[] encoded
  ) {
    int size = Math.min(original.length, encoded.length);

    for (int i = 0; i < size; i++) {
      int a = original[i] & 0xFF;
      int b = encoded[i] & 0xFF;

      if (a == b) {
        continue;
      }

      if (isInsideStringFixByteRange(message, i) && isNullSpacePair(a, b)) {
        continue;
      }

      return false;
    }

    return true;
  }

  private static boolean isNullSpacePair(int a, int b) {
    return (a == 0x00 && b == 0x20) || (a == 0x20 && b == 0x00);
  }

  private static boolean isInsideStringFixByteRange(N2kCompiledMessage message, int byteIndex) {
    for (N2kCompiledField field : message.getFields()) {
      if (field.getFieldType() != N2kFieldType.STRING_FIX) {
        continue;
      }

      int start = field.getStartByte();
      int endExclusive = start + field.getBytesToRead();

      if (byteIndex >= start && byteIndex < endExclusive) {
        return true;
      }
    }
    return false;
  }

  private static void maskNonPayloadBits(byte[] bytes, List<N2kFieldDefinition> fields) {
    boolean[] meaningful = new boolean[bytes.length * 8];

    for (N2kFieldDefinition field : fields) {
      if (field.getFieldType() == N2kFieldType.RESERVED) {
        continue;
      }

      Integer bitOffset = field.getBitOffset();
      Integer bitLength = field.getBitLength();

      if (bitOffset == null || bitLength == null) {
        continue;
      }

      int start = Math.max(0, bitOffset);
      int end = Math.min(meaningful.length, bitOffset + bitLength);

      for (int bit = start; bit < end; bit++) {
        meaningful[bit] = true;
      }
    }

    for (int bit = 0; bit < meaningful.length; bit++) {
      if (!meaningful[bit]) {
        int byteIndex = bit >>> 3;
        int bitInByte = bit & 7; // LSB-first
        bytes[byteIndex] = (byte) (bytes[byteIndex] & ~(1 << bitInByte));
      }
    }
  }


}

