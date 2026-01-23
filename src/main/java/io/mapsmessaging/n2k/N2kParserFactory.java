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

package io.mapsmessaging.n2k;

import io.mapsmessaging.n2k.compile.N2kCompiledRegistry;
import io.mapsmessaging.n2k.compile.N2kCompiler;
import io.mapsmessaging.n2k.model.N2kMessageDefinition;
import io.mapsmessaging.n2k.parser.N2kXmlDialectParser;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

public class N2kParserFactory {

  private static final AtomicReference<N2kCompiledRegistry> registry = new AtomicReference<>();

  public static N2kCompiledRegistry getN2kParser() throws Exception {
    if(registry.get() == null) {
      List<N2kMessageDefinition> messageDefinitions = N2kXmlDialectParser.parseFromClasspath();
      registry.set(N2kCompiler.compile(messageDefinitions));
    }
    return registry.get();
  }

  private N2kParserFactory() {}
}
