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

package io.mapsmessaging.n2k.framing;

import com.google.gson.JsonObject;
import io.mapsmessaging.n2k.codec.N2kMessageParser;
import io.mapsmessaging.n2k.framing.assemblers.FastPacketAssembler;
import io.mapsmessaging.n2k.framing.message.KnownMessage;
import io.mapsmessaging.n2k.framing.message.Message;
import io.mapsmessaging.n2k.framing.message.UnknownMessage;
import io.mapsmessaging.n2k.framing.message.UnknownReason;

import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;

public class FrameHandler {

  private final N2kMessageParser payloadParser;
  private final FastPacketAssembler fastPacketAssembler;

  public FrameHandler(N2kMessageParser payloadParser) {
    this.payloadParser = Objects.requireNonNull(payloadParser, "payloadParser");
    this.fastPacketAssembler = new FastPacketAssembler();
  }

  /**
   * Process one incoming CAN frame.
   *
   * @param canIdentifier Raw CAN identifier. If this includes flags, pass in the identifier bits only.
   * @param extendedFrame True if the CAN frame is Extended Frame Format (29-bit). False for 11-bit.
   * @param dataLengthCode DLC (0..8 for classic CAN / N2K)
   * @param data Data buffer containing at least DLC bytes
   * @return Optional.empty() when incomplete (waiting for fast-packet continuation),
   *         otherwise a complete KnownMessage or UnknownMessage.
   */
  public Optional<Message> onFrame(int canIdentifier, boolean extendedFrame, int dataLengthCode, byte[] data) {
    if (data == null || dataLengthCode <= 0) {
      return Optional.empty();
    }
    if (dataLengthCode > 8) {
      return Optional.of(UnknownMessage.invalidFrame("DLC > 8 for classic CAN/N2K", canIdentifier, dataLengthCode, data));
    }
    if (data.length < dataLengthCode) {
      return Optional.of(UnknownMessage.invalidFrame("data length < DLC", canIdentifier, dataLengthCode, data));
    }

    if (!extendedFrame) {
      byte[] rawPayload = Arrays.copyOf(data, dataLengthCode);
      return Optional.of(new UnknownMessage(
          UnknownReason.NOT_EXTENDED_FRAME,
          null,
          canIdentifier,
          dataLengthCode,
          rawPayload,
          "11-bit CAN frame (not N2K/J1939 extended frame)"
      ));
    }

    if ((canIdentifier & 0xE0000000) != 0) {
      byte[] rawPayload = Arrays.copyOf(data, dataLengthCode);
      return Optional.of(new UnknownMessage(
          UnknownReason.INVALID_IDENTIFIER,
          null,
          canIdentifier,
          dataLengthCode,
          rawPayload,
          "CAN identifier out of 29-bit range"
      ));
    }

    CanId parsedCanId = CanId.parse(canIdentifier);

    byte[] payload = tryAssemblePayload(parsedCanId, dataLengthCode, data);
    if (payload == null) {
      return Optional.empty();
    }

    int pgn = parsedCanId.getPgn();

    if (!payloadParser.getRegistry().getMessagesByPgn().containsKey(pgn)) {
      return Optional.of(new UnknownMessage(
          UnknownReason.UNSUPPORTED_PGN,
          parsedCanId,
          canIdentifier,
          dataLengthCode,
          payload,
          "PGN not supported by parser: " + pgn
      ));
    }

    try {
      JsonObject decoded = payloadParser.decodeToJson(pgn, payload);
      if (decoded == null) {
        return Optional.of(new UnknownMessage(
            UnknownReason.DECODE_FAILED,
            parsedCanId,
            canIdentifier,
            dataLengthCode,
            payload,
            "Parser returned null for PGN: " + pgn
        ));
      }
      return Optional.of(new KnownMessage(parsedCanId, canIdentifier, payload, decoded));
    } catch (Exception exception) {
      return Optional.of(new UnknownMessage(
          UnknownReason.DECODE_FAILED,
          parsedCanId,
          canIdentifier,
          dataLengthCode,
          payload,
          exception.getClass().getSimpleName() + ": " + exception.getMessage()
      ));
    }
  }

  private byte[] tryAssemblePayload(CanId parsedCanId, int dataLengthCode, byte[] data) {
    byte[] frameData = Arrays.copyOf(data, dataLengthCode);

    if (frameData.length < 1) {
      return frameData;
    }

    int firstByte = frameData[0] & 0xFF;
    int frameIndex = firstByte & 0x1F;
    int sequenceId = (firstByte >> 5) & 0x07;

    boolean looksLikeFastPacketStart = frameIndex == 0 && frameData.length >= 2 && ((frameData[1] & 0xFF) > 8);
    boolean hasInProgress = fastPacketAssembler.hasInProgress(parsedCanId, sequenceId);

    if (looksLikeFastPacketStart || hasInProgress) {
      return fastPacketAssembler.accept(parsedCanId, sequenceId, frameIndex, frameData);
    }

    return frameData;
  }
}
