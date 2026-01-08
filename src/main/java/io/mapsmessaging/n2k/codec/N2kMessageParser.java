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
import io.mapsmessaging.n2k.compile.N2kCompiledMessage;
import io.mapsmessaging.n2k.compile.N2kCompiledRegistry;
import io.mapsmessaging.n2k.model.N2kFieldType;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Arrays;

@RequiredArgsConstructor
public class N2kMessageParser {

  private static final java.util.EnumMap<N2kFieldType, Processor> PROCESSORS =
      new java.util.EnumMap<>(N2kFieldType.class);

  static {
    PROCESSORS.put(N2kFieldType.NUMBER, new NumericProcessor());
    PROCESSORS.put(N2kFieldType.FLOAT, new NumericProcessor());
    PROCESSORS.put(N2kFieldType.LOOKUP, new LookupProcessor());
    PROCESSORS.put(N2kFieldType.STRING_FIX, new StringProcessor());
    PROCESSORS.put(N2kFieldType.RESERVED, new ReservedProcessor());
  }

  @Getter private final N2kCompiledRegistry registry;

  public JsonObject decodeToJson(int pgn, byte[] payload) {
    N2kCompiledMessage message = registry.getRequiredMessage(pgn);
    if (message == null) {
      return null;
    }

    JsonObject decoded = new JsonObject();
    int payloadBits = payload.length << 3;

    for (N2kCompiledField field : message.getFields()) {

      int endBitExclusive = field.getBitOffset() + field.getBitLength();
      if (endBitExclusive > payloadBits) {
        break;
      }

      Processor packer = PROCESSORS.get(field.getFieldType());
      if (packer != null) {
        packer.pack(field, payload, decoded);
      }
    }

    JsonObject envelope = new JsonObject();
    envelope.addProperty("pgn", pgn);
    envelope.add("decoded", decoded);

    return envelope;
  }

  public byte[] encodeFromJson(int pgn, JsonObject envelope) {
    N2kCompiledMessage message = registry.getRequiredMessage(pgn);
    if (message == null) {
      return new byte[0];
    }
    if (envelope == null) {
      throw new IllegalArgumentException("Envelope is null");
    }
    if (!envelope.has("decoded") || envelope.get("decoded").isJsonNull()) {
      throw new IllegalArgumentException("Missing 'decoded' object");
    }
    JsonObject decoded = envelope.getAsJsonObject("decoded");

    int payloadLengthBytes = computePayloadLengthBytes(message, decoded);
    byte[] payload = new byte[payloadLengthBytes];
    Arrays.fill(payload, (byte) 0xFF);

    for (N2kCompiledField field : message.getFields()) {
      Processor processor = PROCESSORS.get(field.getFieldType());
      if (processor != null) {
        processor.unpack(field, payload, decoded);
      }
    }

    return payload;
  }

  private static int computePayloadLengthBytes(N2kCompiledMessage message, JsonObject decoded) {
    int requiredBitExclusive = message.getMinimumLengthBytes() << 3;

    for (N2kCompiledField field : message.getFields()) {
      if (!shouldWriteField(field, decoded)) {
        continue;
      }

      int endBitExclusive = field.getBitOffset() + field.getBitLength();
      if (endBitExclusive > requiredBitExclusive) {
        requiredBitExclusive = endBitExclusive;
      }
    }

    int requiredBytes = (requiredBitExclusive + 7) >>> 3;

    if (message.getLengthType() == io.mapsmessaging.n2k.model.N2kMessageLengthType.FIXED) {
      Integer fixedLengthBytes = message.getFixedLengthBytes();
      if (fixedLengthBytes == null) {
        throw new IllegalStateException(
            "FIXED lengthType but fixedLengthBytes is null for PGN " + message.getPgn()
        );
      }

      if (requiredBytes > fixedLengthBytes) {
        throw new IllegalArgumentException(
            "PGN " + message.getPgn() +
                " requires " + requiredBytes +
                " bytes based on provided fields, but fixed length is " + fixedLengthBytes
        );
      }

      return fixedLengthBytes;
    }

    return requiredBytes;
  }


  private static boolean shouldWriteField(N2kCompiledField field, JsonObject decoded) {
    if (field.isReserved()) {
      return true;
    }

    String id = field.getId();
    if (id == null || id.isBlank()) {
      return false;
    }

    if (field.getFieldType() == N2kFieldType.STRING_FIX) {
      return decoded.has(id + "Raw") || decoded.has(id);
    }

    return decoded.has(id);
  }

}
