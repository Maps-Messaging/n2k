# CLAUDE.md - AI Assistant Guide for nmea2000

This document provides guidance for AI assistants working with this codebase.

## Project Overview

This is a Java library for decoding and encoding NMEA 2000 (N2K) message payloads. It parses PGN (Parameter Group Number) definitions from CANboat-style XML metadata, compiles them into an immutable registry, and provides bidirectional JSON codec functionality.

**Key scope limitations:**
- Operates **only** on N2K message payloads (not CAN transport/framing)
- Does not handle fast-packet reassembly or source address management
- Only includes public PGNs from CANboat metadata (no proprietary PGNs)

## Build Commands

```bash
# Build and run tests
mvn clean test

# Build without GPG signing (for development)
mvn -Dgpg.skip=true clean install

# Deploy snapshot
mvn -Dgpg.skip=true clean deploy -Psnapshot

# Deploy release
mvn clean deploy -Prelease

# Run SonarCloud analysis (requires SONAR_TOKEN)
mvn sonar:sonar -Dsonar.login=${SONAR_TOKEN}
```

## Project Structure

```
src/main/java/io/mapsmessaging/n2k/
├── codec/           # Encoding/decoding processors
├── compile/         # PGN definition compilation
├── model/           # Domain model classes
├── parser/          # XML parsing
└── schema/          # JSON Schema generation

src/main/resources/
└── n2k/NMEA_database_1_300.xml    # CANboat PGN definitions

src/test/java/io/mapsmessaging/n2k/
├── BaseTest.java                   # Test utilities and helpers
├── N2kCodecRoundTripTest.java      # Basic round-trip tests
├── N2kRoundTripAllPgnsTest.java    # Dynamic tests for all PGNs
├── N2kRoundTripCanboatLogTest.java # Real-world log validation
└── N2kJsonSchemaValidationTest.java # Schema validation tests
```

## Architecture

### Processing Pipeline

1. **XML Parsing** (`parser/`)
   - `N2kXmlDialectParser` - Parses CANboat XML into `N2kMessageDefinition` objects

2. **Compilation** (`compile/`)
   - `N2kCompiler` - Compiles definitions into optimized `N2kCompiledMessage` objects
   - `N2kCompiledRegistry` - Immutable, thread-safe registry of compiled messages
   - `N2kCompiledField` - Pre-computed field extraction parameters (bit offsets, masks)

3. **Codec** (`codec/`)
   - `N2kMessageParser` - Main entry point for decode/encode operations
   - `N2kBitCodec` - Low-level bit extraction/insertion utilities
   - Processors: `NumericProcessor`, `LookupProcessor`, `StringProcessor`, `ReservedProcessor`

4. **Schema** (`schema/`)
   - `N2kSchemaRegistry` - Generates JSON Schemas for PGNs
   - `N2kJsonSchemaGenerator` - Schema generation from message definitions

### Key Classes

| Class | Purpose |
|-------|---------|
| `N2kXmlDialectParser` | Parse CANboat XML into model objects |
| `N2kCompiler` | Compile model objects into optimized runtime structures |
| `N2kCompiledRegistry` | Thread-safe registry lookup by PGN |
| `N2kMessageParser` | JSON encode/decode operations |
| `N2kBitCodec` | Bit-level payload manipulation |

### Data Flow

```
XML File → N2kXmlDialectParser → List<N2kMessageDefinition>
                                         ↓
                                   N2kCompiler
                                         ↓
                               N2kCompiledRegistry
                                         ↓
                               N2kMessageParser
                                    ↓       ↓
                          decodeToJson  encodeFromJson
```

## Field Types

Defined in `N2kFieldType`:
- `NUMBER` - Numeric values with resolution/offset scaling
- `FLOAT` - Floating-point values
- `LOOKUP` - Enumerated values (decoded as integers)
- `RESERVED` - Reserved/padding bits
- `STRING_FIX` - Fixed-length strings
- `STRING_LAU` - Variable-length strings (Length-And-Unit format)
- `REPEAT_MARKER` - Marks repeating field groups

## Code Conventions

### Immutability
- Compiled structures (`N2kCompiledRegistry`, `N2kCompiledMessage`, `N2kCompiledField`) are immutable
- Use `@Value` (Lombok) for immutable data classes
- Return `List.copyOf()` and `Map.copyOf()` for defensive copies

### Lombok Annotations Used
- `@Value` - Immutable data classes with getters
- `@Builder` - Builder pattern for complex object construction
- `@RequiredArgsConstructor` - Constructor injection
- `@UtilityClass` - Static utility classes

### Thread Safety
- Compiled registries are immutable and safe to share
- Decoders are stateless
- No mutable shared state at runtime

### Error Handling
- Use `IllegalArgumentException` for invalid input data
- Use `IllegalStateException` for internal invariant violations
- All decode errors are explicit (no silent corruption)

## Testing Patterns

### Test Base Class
`BaseTest` provides utilities for round-trip testing:
- `buildRegistry()` - Creates a compiled registry from classpath XML
- `randomRawValue(field, random)` - Generates valid random raw values
- `clampRawValue(field, value)` - Clamps to valid bit range
- `toleranceFor(field)` - Computes comparison tolerance

### Dynamic Tests
`N2kRoundTripAllPgnsTest` uses JUnit 5 `@TestFactory` to generate a test for every PGN:
```java
@TestFactory
Stream<DynamicTest> allCompiledPgns_roundTrip_fixedWidthNumericFields()
```

### Test Resource
- XML definitions: `n2k/NMEA_database_1_300.xml` (classpath)
- Test log file: `src/test/resources/merrimac-actisense-serial-2011.raw`

## Common Tasks

### Adding Support for New Field Types
1. Add enum value to `N2kFieldType`
2. Create new `Processor` implementation in `codec/`
3. Register processor in `N2kMessageParser.PROCESSORS` static block
4. Update `N2kXmlDialectParser.resolveFieldType()` if needed
5. Add tests

### Decoding a Payload (API Usage)
```java
// Load and compile registry (do once, cache globally)
List<N2kMessageDefinition> defs = N2kXmlDialectParser.parseFromClasspath("n2k/NMEA_database_1_300.xml");
N2kCompiledRegistry registry = N2kCompiler.compile(defs);

// Create parser
N2kMessageParser parser = new N2kMessageParser(registry);

// Decode payload bytes to JSON
JsonObject result = parser.decodeToJson(pgn, payloadBytes);

// Encode JSON back to bytes
byte[] encoded = parser.encodeFromJson(pgn, jsonEnvelope);
```

### JSON Envelope Format
```json
{
  "pgn": 127250,
  "decoded": {
    "sid": 9,
    "headingSensorReading": 1.2345,
    "deviation": -0.1,
    "variation": 0.2,
    "headingSensorReference": 2
  }
}
```

## Dependencies

| Dependency | Purpose |
|------------|---------|
| Lombok | Boilerplate reduction |
| Gson | JSON processing |
| JUnit Jupiter | Testing framework |
| json-schema-validator | Schema validation (test scope) |

## CI/CD

Uses Buildkite with two pipelines:
- `pipeline.yml` - Snapshot builds with SonarCloud analysis
- `pipeline_release.yml` - Release builds to Maven Central

## Important Notes for AI Assistants

1. **Bit-level operations are critical** - The `N2kBitCodec` class handles little-endian bit extraction. Be extremely careful when modifying bit manipulation logic.

2. **XML metadata inconsistencies** - The CANboat XML has known issues (conflicting offsets, incorrect signed/unsigned flags). Code must handle these gracefully.

3. **Round-trip testing is essential** - Any codec changes must pass round-trip tests for all PGNs. Use `mvn test` to verify.

4. **Immutability is enforced** - Don't add mutable state to compiled structures. They are designed for concurrent access.

5. **Resolution and offset scaling** - Numeric fields use `value = raw * resolution + offset` for decoding and the inverse for encoding.

6. **Reserved fields** - Always write 0xFF to reserved/padding bits per N2K convention.

7. **No proprietary PGNs** - This library intentionally excludes manufacturer-specific PGNs.
