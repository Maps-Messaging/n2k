package io.mapsmessaging.n2k.compile;

import io.mapsmessaging.n2k.model.N2kFieldDefinition;
import io.mapsmessaging.n2k.model.N2kFieldType;
import io.mapsmessaging.n2k.model.N2kMessageDefinition;
import io.mapsmessaging.n2k.model.N2kMessageLengthType;
import lombok.experimental.UtilityClass;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

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
    N2kFieldType fieldType = fieldDefinition.getFieldType();

    if (fieldDefinition.getBitOffset() == null || fieldDefinition.getBitLength() == null) {
      return false;
    }

    if (fieldType == N2kFieldType.STRING_FIX ||
        fieldType == N2kFieldType.STRING_LAU ||
        fieldType == N2kFieldType.REPEAT_MARKER) {
      return false;
    }

    return true;
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

      N2kCompiledField compiledField = new N2kCompiledField(
          fieldDefinition.getId(),
          fieldDefinition.getName(),
          bitOffset,
          bitLength,
          startByte,
          startBit,
          bytesToRead,
          mask,
          fieldDefinition.isSigned(),
          fieldDefinition.getResolution(),
          fieldDefinition.getOffset(),
          fieldDefinition.getRangeMin(),
          fieldDefinition.getRangeMax(),
          fieldDefinition.getUnit(),
          fieldDefinition.getFieldType(),
          reserved
      );

      if (!reserved) {
        compiledFields.add(compiledField);
      }
    }

    int minimumLengthBytes = computeMinimumLengthBytes(messageDefinition);

    if (messageDefinition.getLengthType() == N2kMessageLengthType.FIXED) {
      Integer fixedLengthBytes = messageDefinition.getFixedLengthBytes();
      if (fixedLengthBytes == null) {
        throw new IllegalArgumentException("FIXED lengthType but fixedLengthBytes is null for PGN " + messageDefinition.getPgn());
      }
      if (fixedLengthBytes < minimumLengthBytes) {
        throw new IllegalArgumentException(
            "Declared lengthBytes " + fixedLengthBytes + " is smaller than minimum " + minimumLengthBytes +
                " for PGN " + messageDefinition.getPgn()
        );
      }
    }

    return new N2kCompiledMessage(
        messageDefinition.getPgn(),
        messageDefinition.getId(),
        messageDefinition.getDescription(),
        messageDefinition.getLengthType(),
        messageDefinition.getFixedLengthBytes(),
        minimumLengthBytes,
        List.copyOf(compiledFields)
    );
  }
}
