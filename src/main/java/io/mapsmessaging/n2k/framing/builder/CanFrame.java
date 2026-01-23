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

package io.mapsmessaging.n2k.framing.builder;

import lombok.Getter;

import java.util.Arrays;

public class CanFrame {

  @Getter
  private final int canIdentifier;
  @Getter
  private final boolean extendedFrame;
  @Getter
  private final int dataLengthCode;
  private final byte[] data;

  public CanFrame(int canIdentifier, boolean extendedFrame, int dataLengthCode, byte[] data) {
    this.canIdentifier = canIdentifier;
    this.extendedFrame = extendedFrame;
    this.dataLengthCode = dataLengthCode;
    this.data = data == null ? null : Arrays.copyOf(data, data.length);
  }

  public byte[] getData() {
    return data == null ? null : Arrays.copyOf(data, data.length);
  }
}
