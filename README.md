# NMEA 2000 (N2K) Payload Codec (Java)

This repository provides a lightweight, schema-driven **NMEA 2000 (N2K) payload**
decoder for Java.

It parses N2K PGN definitions derived from **public CANboat XML metadata**,
compiles message definitions, and provides fast, deterministic decoding of
N2K **payloads only**.

This library is intentionally limited in scope and designed to be composed
into larger N2K-capable systems.

---

## Scope (Read This First)

This library operates **only on NMEA 2000 message payloads**.

It **does**:

- Parse N2K PGN definitions from CANboat-style XML
- Compile PGN definitions into an immutable registry
- Decode N2K payload bytes into typed field maps
- Correctly handle bit-level field packing
- Handle fixed-length and variable-length fields
- Decode repeating fields where defined
- Produce JSON-compatible representations of decoded payloads

It **does not**:

- Handle CAN transport, arbitration, or framing
- Handle fast-packet reassembly
- Manage source addresses or device instances
- Implement PGN transmission or encoding
- Provide coverage for proprietary PGNs

Transport handling and CAN frame management are expected to be provided by
the layer above this codec.

This library is designed to sit **under** a CAN / N2K transport layer.

---

## ⚠️ Important Limitations

### Proprietary PGNs

NMEA 2000 explicitly allows **manufacturer-specific (proprietary) PGNs**.

This library:

- **Only includes public PGNs** defined in CANboat metadata
- **Does not** include proprietary PGNs
- **Does not** attempt to guess or reverse-engineer private payload layouts

If you need proprietary PGNs, you must supply your own definitions or
handle them separately.

---

### XML Inconsistencies

The CANboat XML metadata is not fully consistent.

Known issues include:

- Conflicting or ambiguous bit offsets
- Inconsistent field lengths across revisions
- Fields marked as signed/unsigned incorrectly
- Runtime-sized fields without sufficient metadata
- PGNs whose documented layouts do not match observed payloads

As a result:

- Some PGNs decode correctly
- Some PGNs decode partially
- Some PGNs cannot be decoded reliably without special handling

These issues originate in the source metadata, not the decoder logic.

---

### 🚧 Production Readiness

**This library is not yet production-ready.**

It is currently suitable for:

- Research and exploration
- Tooling and inspection
- Test pipelines
- Decoder validation against real-world logs

It is **not yet suitable** for:

- Safety-critical systems
- Certified marine instrumentation
- Assumed-lossless decoding across all PGNs

Expect ongoing refinement as metadata issues are identified and resolved.

---

## Features

- CANboat XML PGN parsing
- Immutable, thread-safe compiled registries
- Bit-accurate field extraction
- Variable-length and repeating field handling
- Graceful handling of missing or undefined fields
- No shared mutable state at runtime

---

## Getting Started

### Build the registry

```java
N2kCompiledRegistry registry =
    N2kCompiler.compile(xmlInputStream);
```

The registry is immutable and safe to cache globally.

---

## Decoding a Payload

```java
Map<String, Object> decoded =
    registry.parsePayload(pgn, payloadBytes);
```

Decoded values are returned using standard Java types based on the PGN
definition.

Fields that cannot be decoded due to metadata ambiguity may be omitted or
returned as raw values.

---

## Thread Safety

- Compiled registries are immutable
- Decoders are stateless
- No shared mutable runtime state

A single registry instance may be safely:

- Shared across threads
- Cached globally
- Used in high-throughput decoding pipelines

---

## Error Handling

All decoding errors are reported explicitly:

- Unknown PGNs → `IOException`
- Invalid payload lengths → `IOException`
- Bit-range violations → `IOException`
- Truncated or malformed payloads → `IOException`

No unchecked exceptions escape decode paths.

---

## Design Philosophy

- Payloads only, no transport assumptions
- Metadata-driven decoding
- Immutability over defensive copying
- Explicit errors over silent corruption
- Real-world N2K behaviour, not idealised specs

---

## Intended Users

This library is intended for:

- N2K log analysis tools
- Telemetry ingestion pipelines
- Protocol bridges
- Developers exploring N2K payload structures

If you need full NMEA 2000 stack support, use something else.  
If you want a clear, inspectable payload decoder built on public metadata,
this is it.

---

## License

Apache License 2.0

See the `LICENSE` file for details.

