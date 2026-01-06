package io.mapsmessaging.n2k.compile;

import io.mapsmessaging.n2k.model.N2kMessageLengthType;
import lombok.Value;

import java.util.List;

@Value
public class N2kCompiledMessage {
  int pgn;
  String id;
  String description;

  N2kMessageLengthType lengthType;
  Integer fixedLengthBytes;
  int minimumLengthBytes;

  List<N2kCompiledField> fields;

  public int getRequiredLengthBytesForDecode() {
    if (lengthType == N2kMessageLengthType.FIXED) {
      if (fixedLengthBytes == null) {
        throw new IllegalStateException("FIXED lengthType but fixedLengthBytes is null for PGN " + pgn);
      }
      return fixedLengthBytes;
    }
    return minimumLengthBytes;
  }
}
