/*
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
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package io.mapsmessaging.n2k.schema;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.mapsmessaging.n2k.compile.N2kCompiledField;
import io.mapsmessaging.n2k.compile.N2kCompiledMessage;
import io.mapsmessaging.n2k.compile.N2kCompiledRegistry;
import io.mapsmessaging.n2k.model.N2kFieldType;
import lombok.RequiredArgsConstructor;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

@RequiredArgsConstructor
public class N2kSchemaRegistry {

  private final N2kCompiledRegistry registry;

  private volatile Map<Integer, JsonObject> schemasByPgn;

  public JsonObject getSchema(int pgn) {
    Map<Integer, JsonObject> local = schemasByPgn;
    if (local == null) {
      local = buildSchemas();
      schemasByPgn = local;
    }

    JsonObject schema = local.get(pgn);
    if (schema == null) {
      throw new IllegalArgumentException("Unknown PGN: " + pgn);
    }
    return schema;
  }

  public List<JsonObject> getSchemas() {
    Map<Integer, JsonObject> local = schemasByPgn;
    if (local == null) {
      local = buildSchemas();
      schemasByPgn = local;
    }

    List<JsonObject> list = new ArrayList<>(local.values());
    list.sort(Comparator.comparingInt(o -> o.get("properties").getAsJsonObject()
        .get("pgn").getAsJsonObject()
        .get("const").getAsInt()));
    return List.copyOf(list);
  }

  public List<Integer> listPgns() {
    List<Integer> pgns = new ArrayList<>(registry.getMessagesByPgn().keySet());
    pgns.sort(Integer::compare);
    return List.copyOf(pgns);
  }

  private Map<Integer, JsonObject> buildSchemas() {
    Map<Integer, N2kCompiledMessage> messages = registry.getMessagesByPgn();
    java.util.HashMap<Integer, JsonObject> out = new java.util.HashMap<>(messages.size());

    for (N2kCompiledMessage message : messages.values()) {
      int pgn = message.getPgn();
      out.put(pgn, buildSchema(message));
    }

    return Map.copyOf(out);
  }

  private static JsonObject buildSchema(N2kCompiledMessage message) {
    int pgn = message.getPgn();

    JsonObject root = new JsonObject();
    root.addProperty("$id", "n2k/pgn/" + pgn + ".schema.json");
    root.addProperty("type", "object");

    JsonObject properties = new JsonObject();

    JsonObject pgnProperty = new JsonObject();
    pgnProperty.addProperty("const", pgn);
    properties.add("pgn", pgnProperty);

    JsonObject decodedProperty = new JsonObject();
    decodedProperty.addProperty("type", "object");

    JsonObject decodedProperties = new JsonObject();
    JsonArray required = new JsonArray();

    for (N2kCompiledField field : message.getFields()) {
      if (field.isReserved()) {
        continue;
      }

      String id = field.getId();
      if (id == null || id.isBlank()) {
        continue;
      }

      JsonObject fieldSchema = new JsonObject();

      if (field.getFieldType() == N2kFieldType.STRING_FIX || field.getFieldType() == N2kFieldType.STRING_LAU) {
        fieldSchema.addProperty("type", "string");
      }
      else {
        fieldSchema.addProperty("type", "number");
      }

      if (field.getUnit() != null && !field.getUnit().isBlank()) {
        fieldSchema.addProperty("unit", field.getUnit());
      }

      if (field.getName() != null && !field.getName().isBlank()) {
        fieldSchema.addProperty("description", field.getName());
      }

      // IMPORTANT:
      // The N2K XML's RangeMin/RangeMax are *physical* ranges, while in round-trip tests
      // we're often emitting quantized values that can slightly exceed due to scaling/rounding
      // or because the field is really an enum/lookup/source selector with a "range" that isn't strict.
      //
      // So: only emit min/max when we can trust it to be enforceable:
      // - numeric non-string
      // - and resolution is meaningful
      // - and range is not obviously "enum-ish"
      //
      // This prevents false negatives like PGN 126992 'source'.
      if (shouldEmitRangeConstraints(field)) {
        if (field.getRangeMin() != null) {
          fieldSchema.addProperty("minimum", field.getRangeMin());
        }
        if (field.getRangeMax() != null) {
          fieldSchema.addProperty("maximum", field.getRangeMax());
        }
      }

      decodedProperties.add(id, fieldSchema);
      required.add(id);
    }

    decodedProperty.add("properties", decodedProperties);
    decodedProperty.add("required", required);
    decodedProperty.addProperty("additionalProperties", false);

    properties.add("decoded", decodedProperty);

    root.add("properties", properties);

    JsonArray rootRequired = new JsonArray();
    rootRequired.add("pgn");
    rootRequired.add("decoded");
    root.add("required", rootRequired);

    root.addProperty("additionalProperties", false);

    if (message.getId() != null && !message.getId().isBlank()) {
      root.addProperty("title", "N2K PGN " + pgn + " " + message.getId());
    }
    else {
      root.addProperty("title", "N2K PGN " + pgn);
    }

    if (message.getDescription() != null && !message.getDescription().isBlank()) {
      root.addProperty("description", message.getDescription());
    }

    return root;
  }

  private static boolean shouldEmitRangeConstraints(N2kCompiledField field) {
    if (field.getFieldType() == N2kFieldType.STRING_FIX || field.getFieldType() == N2kFieldType.STRING_LAU) {
      return false;
    }

    // Range limits are not trustworthy for LOOKUP-like selectors in the XML
    if (field.getFieldType() == N2kFieldType.LOOKUP) {
      return false;
    }

    if (field.getRangeMin() == null && field.getRangeMax() == null) {
      return false;
    }

    // If the scaling isn't meaningful, range checks tend to be misleading.
    return (field.getResolution() > 0.0);
  }
}
