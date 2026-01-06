package io.mapsmessaging.n2k.parser;

import io.mapsmessaging.n2k.model.N2kMessageLengthType;
import lombok.Value;

@Value
public class N2kMessageLengthParseResult {
  N2kMessageLengthType lengthType;
  Integer fixedLengthBytes;
}
