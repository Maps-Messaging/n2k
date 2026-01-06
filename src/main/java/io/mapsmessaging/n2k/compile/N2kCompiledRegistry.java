package io.mapsmessaging.n2k.compile;

import lombok.Value;

import java.util.Map;

@Value
public class N2kCompiledRegistry {
  Map<Integer, N2kCompiledMessage> messagesByPgn;

  public N2kCompiledMessage getRequiredMessage(int pgn) {
    N2kCompiledMessage message = messagesByPgn.get(pgn);
    if (message == null) {
      throw new IllegalArgumentException("Unknown PGN: " + pgn);
    }
    return message;
  }
}
