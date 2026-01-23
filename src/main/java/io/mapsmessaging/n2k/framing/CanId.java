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

package io.mapsmessaging.n2k.framing;

import lombok.Getter;

@Getter
public class CanId {

  private final int priority;
  private final int pgn;
  private final int sourceAddress;
  private final int destinationAddress;

  private CanId(int priority, int pgn, int sourceAddress, int destinationAddress) {
    this.priority = priority;
    this.pgn = pgn;
    this.sourceAddress = sourceAddress;
    this.destinationAddress = destinationAddress;
  }

  /**
   * Parse a 29-bit extended CAN identifier used by NMEA 2000.
   *
   * Layout (J1939 style):
   * - Priority: bits 26..28 (3 bits)
   * - PGN: derived from PF/PS/DP
   * - Source: bits 0..7
   *
   * PGN rules:
   * - PF < 240 (PDU1): PGN uses PF and DP, PS is destination and excluded from PGN (low byte becomes 0)
   * - PF >= 240 (PDU2): PGN includes PF and PS, destination is "global" (255)
   */
  public static CanId parse(int canIdentifier) {
    int identifier = canIdentifier & 0x1FFFFFFF;

    int priority = (identifier >> 26) & 0x07;
    int pf = (identifier >> 16) & 0xFF;
    int ps = (identifier >> 8) & 0xFF;
    int source = identifier & 0xFF;
    int dataPage = (identifier >> 24) & 0x01;

    int pgn;
    int destination;

    if (pf < 240) {
      // PDU1
      destination = ps;
      pgn = (dataPage << 16) | (pf << 8);
    } else {
      // PDU2
      destination = 255;
      pgn = (dataPage << 16) | (pf << 8) | ps;
    }

    return new CanId(priority, pgn, source, destination);
  }
}