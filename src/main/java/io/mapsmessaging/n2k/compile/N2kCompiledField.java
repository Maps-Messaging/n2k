package io.mapsmessaging.n2k.compile;

import io.mapsmessaging.n2k.model.N2kFieldType;
import lombok.Value;

@Value
public class N2kCompiledField {
  String id;
  String name;

  int bitOffset;
  int bitLength;

  int startByte;
  int startBit;
  int bytesToRead;

  long mask;

  boolean signed;
  double resolution;
  double offset;

  Double rangeMin;
  Double rangeMax;
  String unit;

  N2kFieldType fieldType;

  boolean reserved;
}
