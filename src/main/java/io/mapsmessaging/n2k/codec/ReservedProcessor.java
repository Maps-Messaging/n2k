/*
 *
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
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package io.mapsmessaging.n2k.codec;

import com.google.gson.JsonObject;
import io.mapsmessaging.n2k.compile.N2kCompiledField;

public class ReservedProcessor implements Processor {

  @Override
  public void pack(N2kCompiledField field, byte[] payload, JsonObject decoded) {
    // no-op on decode
  }

  @Override
  public void unpack(N2kCompiledField field, byte[] payload, JsonObject decoded) {
    int bitLength = field.getBitLength();

    // Fast path: whole bytes
    if (field.getStartBit() == 0 && (bitLength & 7) == 0) {
      int start = field.getStartByte();
      int lengthBytes = bitLength >>> 3;

      int endExclusive = Math.min(payload.length, start + lengthBytes);
      for (int i = start; i < endExclusive; i++) {
        payload[i] = (byte) 0xFF;
      }
      return;
    }

    // Slow path: bit aligned, fill using insertBits in <= 63-bit chunks
    int bitsRemaining = bitLength;
    int bitOffset = field.getBitOffset();

    while (bitsRemaining > 0) {
      int chunkBits = Math.min(bitsRemaining, 63);
      long mask = (1L << chunkBits) - 1L;

      int chunkStartByte = bitOffset >>> 3;
      int chunkStartBit = bitOffset & 7;

      int totalBits = chunkStartBit + chunkBits;
      int bytesToRead = (totalBits + 7) >>> 3;

      N2kBitCodec.insertBits(
          payload,
          chunkStartByte,
          chunkStartBit,
          bytesToRead,
          mask,
          -1L
      );

      bitOffset += chunkBits;
      bitsRemaining -= chunkBits;
    }
  }
}
