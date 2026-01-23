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
 *
 */

import com.google.gson.JsonObject;

import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;

public class FrameHandler {

  private final N2kPayloadParser parser;
  private final FastPacketAssembler fastPacketAssembler;

  public FrameHandler(N2kPayloadParser parser) {
    this.parser = Objects.requireNonNull(parser, "parser");
    this.fastPacketAssembler = new FastPacketAssembler();
  }

  /**
   * Process one incoming CAN frame (N2K assumed, 29-bit extended ID).
   *
   * @param canIdentifier 29-bit CAN identifier (already stripped of flags)
   * @param dataLengthCode DLC (0..8 for classic CAN/N2K)
   * @param data Data buffer containing at least DLC bytes
   * @return Optional JsonObject when a complete N2K message payload is available (single-frame or reassembled fast-packet)
   */
  public Optional<JsonObject> onFrame(int canIdentifier, int dataLengthCode, byte[] data) {
    if (data == null || dataLengthCode <= 0) {
      return Optional.empty();
    }
    if (dataLengthCode > 8) {
      throw new IllegalArgumentException("N2K classic CAN frames must have DLC <= 8. Got " + dataLengthCode);
    }
    if (data.length < dataLengthCode) {
      throw new IllegalArgumentException("Data length " + data.length + " < DLC " + dataLengthCode);
    }

    CanId canId = CanId.parse(canIdentifier);

    byte[] payload = tryReassembleFastPacket(canId, dataLengthCode, data);
    if (payload == null) {
      return Optional.empty();
    }

    JsonObject decoded = parser.decodeToJson(canId.getPgn(), payload);
    return Optional.ofNullable(decoded);
  }

  /**
   * Returns:
   * - byte[] payload when complete (single frame OR completed fast-packet)
   * - null if waiting for more frames
   */
  private byte[] tryReassembleFastPacket(CanId canId, int dataLengthCode, byte[] data) {
    // Copy only the DLC bytes, because drivers love giving you 64 bytes of "helpfulness".
    byte[] frameData = Arrays.copyOf(data, dataLengthCode);

    // Fast packet uses first byte as "sequence+frame index" and (on frame index 0) second byte as total length.
    // Heuristic:
    // - If frame index == 0 AND length byte indicates > 8, almost certainly fast-packet start.
    // - Or if we already have an in-progress assembly matching this key, continue it.
    int first = frameData[0] & 0xFF;
    int frameIndex = first & 0x1F;
    int sequenceId = (first >> 5) & 0x07;

    boolean looksLikeFastPacketStart = frameIndex == 0 && frameData.length >= 2 && ((frameData[1] & 0xFF) > 8);
    boolean hasInProgress = fastPacketAssembler.hasInProgress(canId, sequenceId);

    if (looksLikeFastPacketStart || hasInProgress) {
      return fastPacketAssembler.accept(canId, sequenceId, frameIndex, frameData);
    }

    // Single-frame payload:
    return frameData;
  }

  /**
   * Your existing decoder contract.
   */
  public interface N2kPayloadParser {
    JsonObject decodeToJson(int pgn, byte[] payload);
  }
}

