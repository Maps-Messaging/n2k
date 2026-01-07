package io.mapsmessaging.n2k.codec;

import com.google.gson.JsonObject;
import io.mapsmessaging.n2k.compile.N2kCompiledField;
import io.mapsmessaging.n2k.compile.N2kCompiledMessage;
import io.mapsmessaging.n2k.compile.N2kCompiledRegistry;
import io.mapsmessaging.n2k.model.N2kFieldType;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class N2kMessageParser {

  @Getter
  private final N2kCompiledRegistry registry;

  public JsonObject decodeToJson(int pgn, byte[] payload) {
    N2kCompiledMessage message = registry.getRequiredMessage(pgn);
    if(message == null) return null;



    JsonObject decoded = new JsonObject();
    for (N2kCompiledField field : message.getFields()) {

      N2kFieldType fieldType = field.getFieldType();
      if (fieldType != N2kFieldType.NUMBER
          && fieldType != N2kFieldType.FLOAT
          && fieldType != N2kFieldType.LOOKUP) {
        continue;
      }

      long raw = N2kBitCodec.extractBits(
          payload,
          field.getStartByte(),
          field.getStartBit(),
          field.getBytesToRead(),
          field.getMask(),
          field.isSigned(),
          field.getBitLength()
      );

      if (fieldType == N2kFieldType.LOOKUP) {
        decoded.addProperty(field.getId(), (int) (raw & field.getMask()));
        continue;
      }

      double resolution = field.getResolution();
      double offset = field.getOffset();

      // Do NOT infer offset here from rangeMin/rangeMax.
      // This XML contains known-bad ranges (e.g. uint16 with negative min).
      double value = raw * resolution + offset;

      decoded.addProperty(field.getId(), value);
    }


    JsonObject envelope = new JsonObject();
    envelope.addProperty("pgn", pgn);
    envelope.add("decoded", decoded);

    return envelope;
  }

  public byte[] encodeFromJson(int pgn, JsonObject envelope) {
    N2kCompiledMessage message = registry.getRequiredMessage(pgn);

    JsonObject decoded = envelope.getAsJsonObject("decoded");
    if (decoded == null) {
      throw new IllegalArgumentException("Missing 'decoded' object");
    }

    int payloadLengthBytes = computePayloadLengthBytes(message, decoded);
    byte[] payload = new byte[payloadLengthBytes];

    for (N2kCompiledField field : message.getFields()) {

      if (field.isReserved()) {
        int bitLength = field.getBitLength();
        long rawValue;

        if (bitLength >= 64) {
          throw new IllegalStateException(
              "Reserved field too large for 64-bit insert: " + field.getId() + " bitLength=" + bitLength
          );
        }

        rawValue = (bitLength == 64) ? -1L : ((1L << bitLength) - 1L);

        N2kBitCodec.insertBits(
            payload,
            field.getStartByte(),
            field.getStartBit(),
            field.getBytesToRead(),
            field.getMask(),
            rawValue
        );
        continue;
      }

      if (!decoded.has(field.getId())) {
        continue;
      }

      N2kFieldType fieldType = field.getFieldType();
      if (fieldType == N2kFieldType.STRING_FIX || fieldType == N2kFieldType.STRING_LAU) {
        throw new UnsupportedOperationException("String fields not implemented yet for field " + field.getId());
      }

      double numericValue = decoded.get(field.getId()).getAsDouble();

      double resolution = field.getResolution();
      double offset = field.getOffset();

      double unscaled = (numericValue - offset) / resolution;
      long rawValue = Math.round(unscaled);

      long rawMin = field.isSigned()
          ? -(1L << (field.getBitLength() - 1))
          : 0L;

      long rawMax = field.isSigned()
          ? (1L << (field.getBitLength() - 1)) - 1L
          : (1L << field.getBitLength()) - 1L;

      if (rawValue < rawMin) {
        rawValue = rawMin;
      }
      else if (rawValue > rawMax) {
        rawValue = rawMax;
      }

      validateRawValue(field, rawValue);

      N2kBitCodec.insertBits(
          payload,
          field.getStartByte(),
          field.getStartBit(),
          field.getBytesToRead(),
          field.getMask(),
          rawValue
      );
    }

    return payload;
  }

  private static int computePayloadLengthBytes(N2kCompiledMessage message, JsonObject decoded) {
    int requiredBitExclusive = message.getMinimumLengthBytes() << 3;

    for (N2kCompiledField field : message.getFields()) {
      if (field.isReserved()) {
        continue;
      }
      if (!decoded.has(field.getId())) {
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
        throw new IllegalStateException("FIXED lengthType but fixedLengthBytes is null for PGN " + message.getPgn());
      }

      if (requiredBytes > fixedLengthBytes) {
        throw new IllegalArgumentException(
            "PGN " + message.getPgn() + " requires " + requiredBytes + " bytes based on provided fields, but fixed length is " +
                fixedLengthBytes
        );
      }

      return fixedLengthBytes;
    }

    return requiredBytes;
  }

  private static void validateRawValue(N2kCompiledField field, long rawValue) {
    if (!field.isSigned()) {
      if (rawValue < 0) {
        throw new IllegalArgumentException("Unsigned field " + field.getId() + " cannot be negative");
      }
      long max = field.getMask();
      if (rawValue > max) {
        throw new IllegalArgumentException("Field " + field.getId() + " out of range: " + rawValue + " max=" + max);
      }
    }
    else {
      int bitLength = field.getBitLength();
      if (bitLength > 0 && bitLength < 64) {
        long min = -(1L << (bitLength - 1));
        long max = (1L << (bitLength - 1)) - 1L;
        if (rawValue < min || rawValue > max) {
          throw new IllegalArgumentException(
              "Signed field " + field.getId() + " out of range: " + rawValue + " allowed=" + min + ".." + max
          );
        }
      }
    }
  }
}
