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

public class CanIdBuilder {

  private CanIdBuilder() {
  }

  /**
   * Build a 29-bit identifier in J1939/N2K style.
   *
   * Fields:
   * - Priority: bits 26..28
   * - DP: bit 24 (from PGN bit 16)
   * - PF: bits 16..23
   * - PS: bits 8..15 (dest for PDU1, or PGN low byte for PDU2)
   * - SA: bits 0..7
   *
   * PGN rules:
   * - PF < 240 => PDU1 => PS is destination
   * - PF >= 240 => PDU2 => PS comes from PGN low byte, destination implied global
   */
  public static int build(int pgn, int priority, int sourceAddress, int destinationAddress) {
    int prio = priority & 0x07;
    int dp = (pgn >> 16) & 0x01;
    int pf = (pgn >> 8) & 0xFF;

    int ps;
    if (pf < 240) {
      ps = destinationAddress & 0xFF;
    } else {
      ps = pgn & 0xFF;
    }

    int sa = sourceAddress & 0xFF;

    int identifier = 0;
    identifier |= (prio << 26);
    identifier |= (dp << 24);
    identifier |= (pf << 16);
    identifier |= (ps << 8);
    identifier |= sa;

    return identifier & 0x1FFFFFFF;
  }
}
