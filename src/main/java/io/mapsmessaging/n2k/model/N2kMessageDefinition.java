package io.mapsmessaging.n2k.model;

import lombok.Value;

import java.util.List;

@Value
public class N2kMessageDefinition {

  int pgn;

  String id;
  String description;

  int priority;
  String type;

  boolean complete;

  N2kMessageLengthType lengthType;
  Integer fixedLengthBytes;

  List<N2kFieldDefinition> fields;
}
