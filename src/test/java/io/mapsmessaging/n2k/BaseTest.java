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

import io.mapsmessaging.n2k.compile.N2kCompiledField;
import io.mapsmessaging.n2k.compile.N2kCompiledMessage;
import io.mapsmessaging.n2k.compile.N2kCompiledRegistry;
import io.mapsmessaging.n2k.compile.N2kCompiler;
import io.mapsmessaging.n2k.model.N2kMessageDefinition;
import io.mapsmessaging.n2k.parser.N2kXmlDialectParser;
import java.util.List;
import java.util.Random;

public class BaseTest {
  protected static final long BASE_SEED = 0x6b8b4567L;
  protected static final String DIALECT_RESOURCE_PATH = "n2k/NMEA_database_1_300.xml";


  protected static long clampRawValue(N2kCompiledField field, long rawValue) {
    if (!field.isSigned()) {
      if (rawValue < 0L) {
        return 0L;
      }
      long max = field.getMask();
      if (max != 0L && rawValue > max) {
        return max;
      }
      return rawValue;
    }

    int bitLength = field.getBitLength();
    if (bitLength > 0 && bitLength < 64) {
      long min = -(1L << (bitLength - 1));
      long max = (1L << (bitLength - 1)) - 1L;

      if (rawValue < min) {
        return min;
      }
      if (rawValue > max) {
        return max;
      }
    }

    return rawValue;
  }

  protected static double toleranceFor(N2kCompiledField field) {
    double resolution = field.getResolution();
    if (resolution <= 0.0) {
      return 0.0;
    }
    return Math.max(1e-12, resolution * 0.51);
  }

  protected static N2kCompiledField fieldById(N2kCompiledMessage msg, String id) {
    for (N2kCompiledField f : msg.getFields()) {
      if (id.equals(f.getId())) {
        return f;
      }
    }
    return null;
  }
  protected static long randomRawValue(N2kCompiledField field, Random random) {
    RawRange range = computeAllowedRawRange(field);

    if (!range.valid) {
      return 0L;
    }

    if (range.min == range.max) {
      return range.min;
    }

    long span = range.max - range.min;
    long offset = nextLongBounded(random, span + 1L);

    return range.min + offset;
  }

  protected static RawRange computeAllowedRawRange(N2kCompiledField field) {
    int bitLength = field.getBitLength();
    if (bitLength <= 0) {
      return RawRange.invalid();
    }

    long bitMin;
    long bitMax;

    if (field.isSigned()) {
      if (bitLength >= 64) {
        bitMin = Long.MIN_VALUE;
        bitMax = Long.MAX_VALUE;
      }
      else {
        bitMin = -(1L << (bitLength - 1));
        bitMax = (1L << (bitLength - 1)) - 1L;
      }
    }
    else {
      bitMin = 0L;
      bitMax = field.getMask();
      if (bitMax <= 0L) {
        return RawRange.invalid();
      }
    }

    Double rangeMin = field.getRangeMin();
    Double rangeMax = field.getRangeMax();

    if (rangeMin == null && rangeMax == null) {
      return RawRange.of(bitMin, bitMax);
    }

    double resolution = field.getResolution();
    if (resolution <= 0.0) {
      return RawRange.of(bitMin, bitMax);
    }

    double offset = field.getOffset();

    long rawFromRangeMin = bitMin;
    long rawFromRangeMax = bitMax;

    if (rangeMin != null) {
      double unscaledMin = (rangeMin - offset) / resolution;
      rawFromRangeMin = (long) Math.ceil(unscaledMin - 1e-12);
    }

    if (rangeMax != null) {
      double unscaledMax = (rangeMax - offset) / resolution;
      rawFromRangeMax = (long) Math.floor(unscaledMax + 1e-12);
    }

    long min = Math.max(bitMin, rawFromRangeMin);
    long max = Math.min(bitMax, rawFromRangeMax);

    if (!field.isSigned()) {
      long mask = field.getMask();
      min &= mask;
      max &= mask;
    }

    if (min > max) {
      // Range and bit layout disagree; safest is clamp to bit range rather than inventing nonsense.
      return RawRange.of(bitMin, bitMax);
    }

    return RawRange.of(min, max);
  }

  protected static final class RawRange {
    final boolean valid;
    final long min;
    final long max;

    protected RawRange(boolean valid, long min, long max) {
      this.valid = valid;
      this.min = min;
      this.max = max;
    }

    static RawRange of(long min, long max) {
      return new RawRange(true, min, max);
    }

    static RawRange invalid() {
      return new RawRange(false, 0L, 0L);
    }
  }


  protected static long nextLongBounded(Random random, long boundExclusive) {
    if (boundExclusive <= 0L) {
      return 0L;
    }

    long r = random.nextLong();
    long m = boundExclusive - 1L;

    if ((boundExclusive & m) == 0L) {
      return r & m;
    }

    long u = r >>> 1;
    while (u + m - (u % boundExclusive) < 0L) {
      u = (random.nextLong() >>> 1);
    }

    return u % boundExclusive;
  }


  protected static long clampRawValueToSchemaRange(N2kCompiledField field, long rawValue) {
    long clamped = clampRawValue(field, rawValue);

    Double min = field.getRangeMin();
    Double max = field.getRangeMax();

    if (min == null && max == null) {
      return clamped;
    }

    double resolution = field.getResolution();
    if (resolution <= 0.0) {
      return clamped;
    }

    double offset = field.getOffset();

    long minRaw = Long.MIN_VALUE;
    long maxRaw = Long.MAX_VALUE;

    if (min != null) {
      minRaw = Math.round((min - offset) / resolution);
    }
    if (max != null) {
      maxRaw = Math.round((max - offset) / resolution);
    }

    if (minRaw > maxRaw) {
      return clamped;
    }

    if (clamped < minRaw) {
      return minRaw;
    }
    if (clamped > maxRaw) {
      return maxRaw;
    }

    return clamped;
  }



  protected static N2kCompiledRegistry buildRegistry() throws Exception {
    List<N2kMessageDefinition> defs = N2kXmlDialectParser.parseFromClasspath(DIALECT_RESOURCE_PATH);
    return N2kCompiler.compile(defs);
  }
}
