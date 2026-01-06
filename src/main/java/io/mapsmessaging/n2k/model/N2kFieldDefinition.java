package io.mapsmessaging.n2k.model;

import lombok.Value;

@Value
public class N2kFieldDefinition {

  int order;

  String id;
  String name;

  Integer bitOffset;
  Integer bitLength;

  Integer bitStart;

  boolean signed;

  N2kFieldType fieldType;

  double resolution;
  double offset;

  Double rangeMin;
  Double rangeMax;

  String unit;
  String typeInPdf;

  public boolean hasBitOffset() {
    return bitOffset != null;
  }

  public boolean hasBitLength() {
    return bitLength != null;
  }
}
