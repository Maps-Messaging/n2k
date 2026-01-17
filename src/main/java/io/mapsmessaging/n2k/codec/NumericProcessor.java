/*
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
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package io.mapsmessaging.n2k.codec;

import com.google.gson.JsonObject;
import io.mapsmessaging.n2k.compile.N2kCompiledField;

public class NumericProcessor implements Processor {

  public void pack(N2kCompiledField field, byte[] payload, JsonObject decoded) {
    long raw =
        N2kBitCodec.extractBits(
            payload,
            field.getStartByte(),
            field.getStartBit(),
            field.getBytesToRead(),
            field.getMask(),
            field.isSigned(),
            field.getBitLength());
    double value = raw * field.getResolution() + field.getOffset();
    decoded.addProperty(field.getId(), value);
  }

  public void unpack(N2kCompiledField field, byte[] payload, JsonObject decoded) {
    if (!decoded.has(field.getId()) || decoded.get(field.getId()).isJsonNull()) {
      return;
    }

    double numericValue = decoded.get(field.getId()).getAsDouble();

    double resolution = field.getResolution();
    if (resolution == 0.0) {
      throw new IllegalStateException("Resolution is zero for numeric field " + field.getId());
    }

    double offset = field.getOffset();

    double unscaled = (numericValue - offset) / resolution;
    long rawValue = Math.round(unscaled);

    if (rawValue < field.getRawMin()) {
      rawValue = field.getRawMin();
    }
    else if (rawValue > field.getRawMax()) {
      rawValue = field.getRawMax();
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

  public NumericProcessor() {}
}
