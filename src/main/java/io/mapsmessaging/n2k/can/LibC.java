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

import com.sun.jna.Library;
import com.sun.jna.Pointer;
import com.sun.jna.Structure;

interface LibC extends Library {
  int socket(int domain, int type, int protocol);
  int bind(int socketFileDescriptor, Structure address, int addressLength);
  int ioctl(int fileDescriptor, int request, Pointer argumentPointer);
  int read(int fileDescriptor, Pointer buffer, int count);
  int write(int fileDescriptor, Pointer buffer, int count);
  int close(int fileDescriptor);
}
