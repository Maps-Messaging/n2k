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

package io.mapsmessaging.n2k.can;

import java.time.Instant;

public final class CanDump {

  private static final int CAN_EFF_FLAG = 0x80000000;
  private static final int CAN_EFF_MASK = 0x1FFFFFFF;

  public static void main(String[] args) throws Exception {
    String interfaceName = args.length > 0 ? args[0] : "can0";

    try (SocketCanReader reader = new SocketCanReader(interfaceName)) {
      while (true) {
        CanFrame frame = reader.readFrame();

        long nowNanos = System.nanoTime();
        String timestamp = Instant.now().toString();

        int rawId = frame.canIdentifier();
        boolean extended = (rawId & CAN_EFF_FLAG) != 0;
        int id = extended ? (rawId & CAN_EFF_MASK) : (rawId & 0x7FF);

        StringBuilder line = new StringBuilder(128);
        line.append(timestamp);
        line.append(" ");
        line.append(interfaceName);
        line.append(" ");
        line.append(extended ? String.format("%08X", id) : String.format("%03X", id));
        line.append(" [");
        line.append(frame.dataLengthCode());
        line.append("]");

        byte[] data = frame.data();
        int length = frame.dataLengthCode();
        for (int i = 0; i < length && i < 8; i++) {
          line.append(" ");
          line.append(String.format("%02X", data[i]));
        }

        line.append("  (t=");
        line.append(nowNanos);
        line.append("ns)");

        System.out.println(line);
      }
    }
  }
}
