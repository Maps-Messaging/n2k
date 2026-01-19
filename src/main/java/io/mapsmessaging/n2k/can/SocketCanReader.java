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

import com.sun.jna.Native;

import java.io.Closeable;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import static io.mapsmessaging.n2k.can.IfReq.IFNAMSIZ;

public final class SocketCanReader implements Closeable {

  private static final LibC LIB_C = Native.load("c", LibC.class);

  // socket() constants
  private static final int AF_CAN = 29;
  private static final int SOCK_RAW = 3;
  private static final int CAN_RAW = 1;

  // ioctl() constants
  private static final int SIOCGIFINDEX = 0x8933;


  private final int socketFileDescriptor;

  public SocketCanReader(String interfaceName) throws IOException {
    int fileDescriptor = LIB_C.socket(AF_CAN, SOCK_RAW, CAN_RAW);
    if (fileDescriptor < 0) {
      throw new IOException("socket(AF_CAN,SOCK_RAW,CAN_RAW) failed errno=" + Native.getLastError());
    }

    int interfaceIndex = resolveInterfaceIndex(fileDescriptor, interfaceName);

    SockAddrCan socketAddress = new SockAddrCan();
    socketAddress.canFamily = (short) AF_CAN;
    socketAddress.canInterfaceIndex = interfaceIndex;
    socketAddress.address = new byte[8];
    socketAddress.write();

    int bindResult = LIB_C.bind(fileDescriptor, socketAddress, socketAddress.size());
    if (bindResult != 0) {
      int errno = Native.getLastError();
      LIB_C.close(fileDescriptor);
      throw new IOException("bind(" + interfaceName + ") failed errno=" + errno);
    }

    this.socketFileDescriptor = fileDescriptor;
  }

  public CanFrame readFrame() throws IOException {
    NativeCanFrame nativeFrame = new NativeCanFrame();
    int bytesRead = LIB_C.read(this.socketFileDescriptor, nativeFrame.getPointer(), nativeFrame.size());
    if (bytesRead < 0) {
      throw new IOException("read(can_frame) failed errno=" + Native.getLastError());
    }
    if (bytesRead != nativeFrame.size()) {
      throw new IOException("Short read: " + bytesRead + " bytes (expected " + nativeFrame.size() + ")");
    }

    nativeFrame.read();

    int canIdentifier = nativeFrame.canIdentifier;
    int dataLengthCode = nativeFrame.dataLengthCode & 0xFF;
    byte[] data = Arrays.copyOf(nativeFrame.data, 8);

    return new CanFrame(canIdentifier, dataLengthCode, data);
  }

  public void writeFrame(CanFrame frame) throws IOException {
    writeFrame(frame.canIdentifier(), frame.dataLengthCode(), frame.data());
  }

  public void writeFrame(int canIdentifier, int dataLengthCode, byte[] data) throws IOException {
    if (dataLengthCode < 0 || dataLengthCode > 8) {
      throw new IllegalArgumentException("dataLengthCode must be 0..8");
    }
    if (data == null) {
      throw new IllegalArgumentException("data must not be null");
    }

    NativeCanFrame nativeFrame = new NativeCanFrame();
    nativeFrame.canIdentifier = canIdentifier;
    nativeFrame.dataLengthCode = (byte) dataLengthCode;

    Arrays.fill(nativeFrame.data, (byte) 0);
    int copyLength = Math.min(dataLengthCode, Math.min(8, data.length));
    System.arraycopy(data, 0, nativeFrame.data, 0, copyLength);

    nativeFrame.write();

    int bytesWritten = LIB_C.write(this.socketFileDescriptor, nativeFrame.getPointer(), nativeFrame.size());
    if (bytesWritten < 0) {
      throw new IOException("write(can_frame) failed errno=" + Native.getLastError());
    }
    if (bytesWritten != nativeFrame.size()) {
      throw new IOException("Short write: " + bytesWritten + " bytes (expected " + nativeFrame.size() + ")");
    }
  }

  @Override
  public void close() throws IOException {
    int result = LIB_C.close(this.socketFileDescriptor);
    if (result != 0) {
      throw new IOException("close() failed errno=" + Native.getLastError());
    }
  }

  private static int resolveInterfaceIndex(int socketFileDescriptor, String interfaceName) throws IOException {
    IfReq ifRequest = new IfReq();
    Arrays.fill(ifRequest.interfaceName, (byte) 0);

    byte[] nameBytes = interfaceName.getBytes(StandardCharsets.US_ASCII);
    int copyLength = Math.min(nameBytes.length, IFNAMSIZ - 1);
    System.arraycopy(nameBytes, 0, ifRequest.interfaceName, 0, copyLength);

    ifRequest.interfaceIndex = 0;
    Arrays.fill(ifRequest.padding, (byte) 0);
    ifRequest.write();

    int ioctlResult = LIB_C.ioctl(socketFileDescriptor, SIOCGIFINDEX, ifRequest.getPointer());
    if (ioctlResult != 0) {
      throw new IOException("ioctl(SIOCGIFINDEX," + interfaceName + ") failed errno=" + Native.getLastError());
    }

    ifRequest.read();
    if (ifRequest.interfaceIndex <= 0) {
      throw new IOException("Interface index not resolved for " + interfaceName);
    }

    return ifRequest.interfaceIndex;
  }

}
