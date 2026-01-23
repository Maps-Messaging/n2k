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

import com.google.gson.JsonObject;
import io.mapsmessaging.n2k.codec.N2kMessageParser;
import io.mapsmessaging.n2k.framing.builder.CanFrame;
import io.mapsmessaging.n2k.framing.builder.CanIdBuilder;
import io.mapsmessaging.n2k.framing.builder.SequenceProvider;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class FramePacker {

  private final N2kMessageParser  payloadEncoder;
  private final SequenceProvider sequenceProvider;

  public FramePacker(N2kMessageParser payloadEncoder) {
    this(payloadEncoder, new SequenceProvider());
  }

  public FramePacker(N2kMessageParser payloadEncoder, SequenceProvider sequenceProvider) {
    this.payloadEncoder = Objects.requireNonNull(payloadEncoder, "payloadEncoder");
    this.sequenceProvider = Objects.requireNonNull(sequenceProvider, "sequenceProvider");
  }

  /**
   * Packs an N2K message (PGN + JSON) into CAN frames (8 bytes payload per frame).
   *
   * @param pgn N2K PGN
   * @param priority 0..7
   * @param sourceAddress 0..255
   * @param destinationAddress 0..255 (used only for PF < 240; otherwise treated as global for PDU2)
   * @param json Payload data as JSON to encode
   * @return List of CAN frames ready to send
   */
  public List<CanFrame> pack(int pgn, int priority, int sourceAddress, int destinationAddress, JsonObject json) {
    Objects.requireNonNull(json, "json");

    byte[] payload = payloadEncoder.encodeFromJson(pgn, json);
    if (payload == null) {
      throw new IllegalStateException("Payload encoder returned null for PGN " + pgn);
    }

    int canIdentifier = CanIdBuilder.build(pgn, priority, sourceAddress, destinationAddress);

    if (payload.length <= 8) {
      byte[] data = new byte[payload.length];
      System.arraycopy(payload, 0, data, 0, payload.length);
      return List.of(new CanFrame(canIdentifier, true, payload.length, data));
    }

    return packFastPacket(pgn, canIdentifier, sourceAddress, destinationAddress, payload);
  }

  private List<CanFrame> packFastPacket(
      int pgn,
      int canIdentifier,
      int sourceAddress,
      int destinationAddress,
      byte[] payload
  ) {
    int sequenceId = sequenceProvider.nextSequenceId(pgn, sourceAddress, destinationAddress);

    List<CanFrame> frames = new ArrayList<>();

    int totalLength = payload.length;
    int payloadIndex = 0;

    // Frame 0: byte0 = seq+index(0), byte1 = totalLength, bytes2..7 = first 6 payload bytes
    byte[] frame0 = new byte[8];
    frame0[0] = (byte) (((sequenceId & 0x07) << 5));
    frame0[1] = (byte) (totalLength & 0xFF);

    int firstChunk = Math.min(6, totalLength);
    if (firstChunk > 0) {
      System.arraycopy(payload, 0, frame0, 2, firstChunk);
      payloadIndex += firstChunk;
    }

    frames.add(new CanFrame(canIdentifier, true, 8, frame0));

    // Subsequent frames: byte0 = seq+index(n), bytes1..7 = next 7 payload bytes
    int frameIndex = 1;
    while (payloadIndex < totalLength) {
      byte[] frame = new byte[8];
      frame[0] = (byte) (((sequenceId & 0x07) << 5) | (frameIndex & 0x1F));

      int chunk = Math.min(7, totalLength - payloadIndex);
      System.arraycopy(payload, payloadIndex, frame, 1, chunk);
      payloadIndex += chunk;

      // N2K fast-packet frames are typically sent as full 8-byte frames (DLC=8)
      frames.add(new CanFrame(canIdentifier, true, 8, frame));

      frameIndex++;
      if (frameIndex > 31) {
        throw new IllegalStateException("Fast packet exceeded 32 frames for PGN " + pgn + " (payload length " + totalLength + ")");
      }
    }

    return frames;
  }

}
