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

package io.mapsmessaging.n2k.framing.assemblers;


import io.mapsmessaging.n2k.framing.CanId;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class FastPacketAssembler {

  private final Map<FastPacketKey, FastPacketAssembly> inProgress;

  public FastPacketAssembler() {
    this.inProgress = new HashMap<>();
  }

  public boolean hasInProgress(CanId canId, int sequenceId) {
    FastPacketKey key = FastPacketKey.from(canId, sequenceId);
    return inProgress.containsKey(key);
  }

  /**
   * Accept one fast-packet CAN frame.
   *
   * @param canId Parsed CAN ID (PGN/source/dest)
   * @param sequenceId 3-bit sequence (0..7)
   * @param frameIndex 5-bit frame index (0..31) but practically starts at 0 and increments
   * @param frameData DLC bytes (<= 8)
   * @return full payload when complete, else null
   */
  public byte[] accept(CanId canId, int sequenceId, int frameIndex, byte[] frameData) {
    Objects.requireNonNull(canId, "canId");
    Objects.requireNonNull(frameData, "frameData");

    FastPacketKey key = FastPacketKey.from(canId, sequenceId);

    if (frameIndex == 0) {
      if (frameData.length < 2) {
        inProgress.remove(key);
        return null;
      }

      int totalLength = frameData[1] & 0xFF;
      if (totalLength <= 0) {
        inProgress.remove(key);
        return null;
      }

      FastPacketAssembly assembly = new FastPacketAssembly(totalLength);
      inProgress.put(key, assembly);

      // Frame 0 contributes 6 bytes starting at index 2
      int bytesFromFrame = Math.min(6, frameData.length - 2);
      if (bytesFromFrame > 0) {
        assembly.append(frameData, 2, bytesFromFrame);
      }

      if (assembly.isComplete()) {
        inProgress.remove(key);
        return assembly.toPayload();
      }

      return null;
    }

    FastPacketAssembly assembly = inProgress.get(key);
    if (assembly == null) {
      // We missed the start frame, so we can't reassemble this message.
      return null;
    }

    int bytesFromFrame = Math.min(7, frameData.length - 1);
    if (bytesFromFrame > 0) {
      assembly.append(frameData, 1, bytesFromFrame);
    }

    if (assembly.isComplete()) {
      inProgress.remove(key);
      return assembly.toPayload();
    }

    return null;
  }

  private static final class FastPacketKey {
    private final int pgn;
    private final int source;
    private final int destination;
    private final int sequenceId;

    private FastPacketKey(int pgn, int source, int destination, int sequenceId) {
      this.pgn = pgn;
      this.source = source;
      this.destination = destination;
      this.sequenceId = sequenceId;
    }

    private static FastPacketKey from(CanId canId, int sequenceId) {
      return new FastPacketKey(
          canId.getPgn(),
          canId.getSourceAddress(),
          canId.getDestinationAddress(),
          sequenceId & 0x07
      );
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (!(o instanceof FastPacketKey other)) {
        return false;
      }
      return pgn == other.pgn
          && source == other.source
          && destination == other.destination
          && sequenceId == other.sequenceId;
    }

    @Override
    public int hashCode() {
      int result = Integer.hashCode(pgn);
      result = 31 * result + Integer.hashCode(source);
      result = 31 * result + Integer.hashCode(destination);
      result = 31 * result + Integer.hashCode(sequenceId);
      return result;
    }
  }

  private static final class FastPacketAssembly {
    private final byte[] payload;
    private int writeIndex;

    private FastPacketAssembly(int totalLength) {
      this.payload = new byte[totalLength];
      this.writeIndex = 0;
    }

    private void append(byte[] src, int offset, int length) {
      int remaining = payload.length - writeIndex;
      int toCopy = Math.min(remaining, length);
      if (toCopy <= 0) {
        return;
      }
      System.arraycopy(src, offset, payload, writeIndex, toCopy);
      writeIndex += toCopy;
    }

    private boolean isComplete() {
      return writeIndex >= payload.length;
    }

    private byte[] toPayload() {
      if (writeIndex == payload.length) {
        return payload;
      }
      return Arrays.copyOf(payload, writeIndex);
    }
  }
}
