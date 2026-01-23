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

package io.mapsmessaging.n2k.framing.message;

import io.mapsmessaging.n2k.framing.CanId;
import lombok.Getter;

import java.util.Arrays;

@Getter
public class UnknownMessage implements Message {

  private final UnknownReason reason;
  private final CanId canId;
  private final int rawCanIdentifier;
  private final int dataLengthCode;
  private final byte[] payload;
  private final String detail;

  public UnknownMessage(
      UnknownReason reason,
      CanId canId,
      int rawCanIdentifier,
      int dataLengthCode,
      byte[] payload,
      String detail
  ) {
    this.reason = reason;
    this.canId = canId;
    this.rawCanIdentifier = rawCanIdentifier;
    this.dataLengthCode = dataLengthCode;
    this.payload = payload == null ? null : Arrays.copyOf(payload, payload.length);
    this.detail = detail;
  }

  public static UnknownMessage invalidFrame(String detail, int rawCanIdentifier, int dataLengthCode, byte[] data) {
    byte[] rawPayload = data == null ? null : Arrays.copyOf(data, Math.min(dataLengthCode, data.length));
    return new UnknownMessage(
        UnknownReason.INVALID_FRAME,
        null,
        rawCanIdentifier,
        dataLengthCode,
        rawPayload,
        detail
    );
  }
}
