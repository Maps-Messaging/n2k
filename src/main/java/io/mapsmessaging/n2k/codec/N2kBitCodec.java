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

import lombok.experimental.UtilityClass;

@UtilityClass
public class N2kBitCodec {

  public static long extractBits(
      byte[] buffer,
      int startByte,
      int startBit,
      int bytesToRead,
      long mask,
      boolean signed,
      int bitLength
  ) {
    long value = 0L;

    for (int i = 0; i < bytesToRead; i++) {
      int index = startByte + i;
      if (index >= buffer.length) {
        break;
      }
      value |= (buffer[index] & 0xFFL) << (8 * i);
    }

    value >>>= startBit;
    value &= mask;

    if (signed) {
      return signExtend(value, bitLength);
    }

    return value;
  }

  public static long extractUnsigned(byte[] payload, int startByte, int startBit, int bytesToRead, long mask) {
    long value = 0L;

    for (int i = 0; i < bytesToRead; i++) {
      int index = startByte + i;
      if (index >= payload.length) {
        break;
      }
      long b = payload[index] & 0xFFL;
      value |= (b << (8 * i));
    }

    long shifted = value >>> startBit;
    return shifted & mask;
  }

  public static long signExtend(long raw, int bitLength) {
    if (bitLength <= 0 || bitLength >= 64) {
      return raw;
    }

    long signBit = 1L << (bitLength - 1);
    if ((raw & signBit) == 0L) {
      return raw;
    }

    long extensionMask = -(1L << bitLength);
    return raw | extensionMask;
  }

  public static void insertBits(byte[] payload, int startByte, int startBit, int bytesToWrite, long mask, long rawValue) {
    long container = 0L;

    for (int i = 0; i < bytesToWrite; i++) {
      int index = startByte + i;
      if (index >= payload.length) {
        break;
      }
      long b = payload[index] & 0xFFL;
      container |= (b << (8 * i));
    }

    long shiftedMask = (mask << startBit);
    container &= ~shiftedMask;

    long shiftedValue = (rawValue & mask) << startBit;
    container |= shiftedValue;

    for (int i = 0; i < bytesToWrite; i++) {
      int index = startByte + i;
      if (index >= payload.length) {
        break;
      }
      payload[index] = (byte) ((container >>> (8 * i)) & 0xFFL);
    }
  }
}
