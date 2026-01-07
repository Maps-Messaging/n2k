package io.mapsmessaging.n2k.compile;

import lombok.Value;

import java.util.Map;

@Value
public class N2kCompiledRegistry {
  Map<Integer, N2kCompiledMessage> messagesByPgn;

  public N2kCompiledMessage getRequiredMessage(int pgn) {
    return messagesByPgn.get(pgn);
  }
}
