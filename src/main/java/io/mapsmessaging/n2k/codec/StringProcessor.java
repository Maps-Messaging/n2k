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
import io.mapsmessaging.n2k.compile.N2kCompiledField;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public class StringProcessor implements Processor {

  @Override
  public void pack(N2kCompiledField field, byte[] payload, JsonObject decoded) {
    if (field.getStartBit() != 0) {
      throw new UnsupportedOperationException(
          "STRING_FIX must be byte-aligned: " + field.getId() + " startBit=" + field.getStartBit()
      );
    }

    int start = field.getStartByte();
    int length = field.getBytesToRead();

    int endExclusive = Math.min(payload.length, start + length);
    int safeLength = Math.max(0, endExclusive - start);

    if (safeLength <= 0) {
      decoded.addProperty(field.getId(), "");
      return;
    }

    byte[] rawBytes = Arrays.copyOfRange(payload, start, start + safeLength);

    String text = new String(rawBytes, StandardCharsets.ISO_8859_1);
    text = trimRight(text, '\0', ' ');
    decoded.addProperty(field.getId(), text);
  }

  @Override
  public void unpack(N2kCompiledField field, byte[] payload, JsonObject decoded) {
    if (field.getStartBit() != 0) {
      throw new UnsupportedOperationException(
          "STRING_FIX must be byte-aligned: " + field.getId() + " startBit=" + field.getStartBit()
      );
    }

    int start = field.getStartByte();
    int length = field.getBytesToRead();

    int endExclusive = Math.min(payload.length, start + length);
    int safeLength = Math.max(0, endExclusive - start);
    if (safeLength <= 0) {
      return;
    }

    // Deterministic padding for STRING_FIX: spaces, not NULs, not 0xFF.
    Arrays.fill(payload, start, start + safeLength, (byte) 0x20);

    if (!decoded.has(field.getId()) || decoded.get(field.getId()).isJsonNull()) {
      return;
    }

    String text = decoded.get(field.getId()).getAsString();
    if (text == null || text.isEmpty()) {
      return;
    }

    byte[] source = text.getBytes(StandardCharsets.ISO_8859_1);
    int copyLength = Math.min(safeLength, source.length);
    System.arraycopy(source, 0, payload, start, copyLength);
  }

  private static String trimRight(String input, char... trimChars) {
    int end = input.length();
    while (end > 0) {
      char c = input.charAt(end - 1);
      boolean match = false;
      for (char t : trimChars) {
        if (c == t) {
          match = true;
          break;
        }
      }
      if (!match) {
        break;
      }
      end--;
    }
    return (end == input.length()) ? input : input.substring(0, end);
  }

  public StringProcessor() {}
}
