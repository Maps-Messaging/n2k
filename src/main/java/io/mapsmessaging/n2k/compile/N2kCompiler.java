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

package io.mapsmessaging.n2k.compile;

import io.mapsmessaging.n2k.model.N2kFieldDefinition;
import io.mapsmessaging.n2k.model.N2kFieldType;
import io.mapsmessaging.n2k.model.N2kMessageDefinition;
import io.mapsmessaging.n2k.model.N2kMessageLengthType;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import lombok.experimental.UtilityClass;

@UtilityClass
public class N2kCompiler {

  public static N2kCompiledRegistry compile(List<N2kMessageDefinition> messageDefinitions) {
    Map<Integer, N2kCompiledMessage> messagesByPgn = new HashMap<>(messageDefinitions.size());

    for (N2kMessageDefinition messageDefinition : messageDefinitions) {
      N2kCompiledMessage compiledMessage = compileMessage(messageDefinition);
      messagesByPgn.put(compiledMessage.getPgn(), compiledMessage);
    }

    return new N2kCompiledRegistry(Map.copyOf(messagesByPgn));
  }

  private static boolean isCompileTimeFixedField(N2kFieldDefinition fieldDefinition) {
    return fieldDefinition.getBitOffset() != null
        && fieldDefinition.getBitLength() != null
        && fieldDefinition.getFieldType() != N2kFieldType.STRING_LAU
        && fieldDefinition.getFieldType() != N2kFieldType.REPEAT_MARKER;
  }

  private static int computeMinimumLengthBytes(N2kMessageDefinition messageDefinition) {
    int maxBitExclusive = 0;

    for (N2kFieldDefinition field : messageDefinition.getFields()) {

      if (!isCompileTimeFixedField(field)) {
        continue;
      }

      int endBit = field.getBitOffset() + field.getBitLength();
      if (endBit > maxBitExclusive) {
        maxBitExclusive = endBit;
      }
    }

    return (maxBitExclusive + 7) >>> 3;
  }

  private static N2kCompiledMessage compileMessage(N2kMessageDefinition messageDefinition) {
    List<N2kCompiledField> compiledFields = new ArrayList<>();
    HashSet<String> seenIds = new HashSet<>();

    for (N2kFieldDefinition fieldDefinition : messageDefinition.getFields()) {

      if (!isCompileTimeFixedField(fieldDefinition)) {
        continue;
      }

      N2kFieldType fieldType = fieldDefinition.getFieldType();
      boolean reserved = fieldType == N2kFieldType.RESERVED;

      String id = fieldDefinition.getId();
      if (!reserved) {
        if (id == null || id.isBlank()) {
          continue;
        }
        if (!seenIds.add(id)) {
          continue;
        }
      }

      int bitOffset = fieldDefinition.getBitOffset();
      int bitLength = fieldDefinition.getBitLength();

      int startByte = bitOffset >>> 3;
      int startBit = bitOffset & 7;

      int totalBits = startBit + bitLength;
      int bytesToRead = (totalBits + 7) >>> 3;

      long mask;
      if (bitLength == 64) {
        mask = -1L;
      }
      else if (bitLength > 0 && bitLength < 64) {
        mask = (1L << bitLength) - 1L;
      }
      else {
        mask = 0L;
      }

      double resolution = fieldDefinition.getResolution();
      double offset = fieldDefinition.getOffset();

      Double rangeMin = fieldDefinition.getRangeMin();
      Double rangeMax = fieldDefinition.getRangeMax();

      long rawMin;
      long rawMax;

      if (fieldDefinition.isSigned()) {
        rawMin = -(1L << (bitLength - 1));
        rawMax =  (1L << (bitLength - 1)) - 1L;
      }
      else {
        rawMin = 0L;
        rawMax = (bitLength == 64) ? -1L : (1L << bitLength) - 1L;
      }

      N2kCompiledField compiledField =
          N2kCompiledField.builder()
              .id(fieldDefinition.getId())
              .name(fieldDefinition.getName())
              .bitOffset(bitOffset)
              .bitLength(bitLength)
              .startByte(startByte)
              .startBit(startBit)
              .bytesToRead(bytesToRead)
              .mask(mask)
              .signed(fieldDefinition.isSigned())
              .resolution(resolution)
              .offset(offset)
              .rangeMin(rangeMin)
              .rangeMax(rangeMax)
              .unit(fieldDefinition.getUnit())
              .fieldType(fieldType)
              .reserved(reserved)
              .rawMin(rawMin)
              .rawMax(rawMax)
              .build();
      compiledFields.add(compiledField);
    }

    int minimumLengthBytes = computeMinimumLengthBytes(messageDefinition);

    if (messageDefinition.getLengthType() == N2kMessageLengthType.FIXED) {
      Integer fixedLengthBytes = messageDefinition.getFixedLengthBytes();
      if (fixedLengthBytes == null) {
        throw new IllegalArgumentException(
            "FIXED lengthType but fixedLengthBytes is null for PGN " + messageDefinition.getPgn()
        );
      }
      if (fixedLengthBytes < minimumLengthBytes) {
        throw new IllegalArgumentException(
            "Declared lengthBytes " + fixedLengthBytes + " is smaller than minimum " + minimumLengthBytes +
                " for PGN " + messageDefinition.getPgn()
        );
      }
    }

    return N2kCompiledMessage.builder()
        .pgn(messageDefinition.getPgn())
        .id(messageDefinition.getId())
        .description(messageDefinition.getDescription())
        .lengthType(messageDefinition.getLengthType())
        .fixedLengthBytes(messageDefinition.getFixedLengthBytes())
        .minimumLengthBytes(minimumLengthBytes)
        .fields(compiledFields)
        .definitions(messageDefinition.getFields())
        .build();

  }
}
