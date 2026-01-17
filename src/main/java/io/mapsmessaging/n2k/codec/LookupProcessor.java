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

public class LookupProcessor implements Processor {

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
    decoded.addProperty(field.getId(),(int) (raw & field.getMask()));
  }

  public void unpack(N2kCompiledField field, byte[] payload, JsonObject decoded) {
    if (!decoded.has(field.getId()) || decoded.get(field.getId()).isJsonNull()) {
      return;
    }

    long rawValue = decoded.get(field.getId()).getAsLong();

    long max = (field.getBitLength() >= 64) ? -1L : ((1L << field.getBitLength()) - 1L);
    if (field.getBitLength() < 64 && rawValue > max) {
      rawValue = max;
    }
    if (rawValue < 0) {
      rawValue = 0;
    }

    N2kBitCodec.insertBits(
        payload,
        field.getStartByte(),
        field.getStartBit(),
        field.getBytesToRead(),
        field.getMask(),
        rawValue
    );
  }

  public LookupProcessor(){}
}
