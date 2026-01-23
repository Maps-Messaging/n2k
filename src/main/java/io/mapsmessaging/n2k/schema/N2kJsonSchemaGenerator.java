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

package io.mapsmessaging.n2k.schema;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.mapsmessaging.n2k.model.N2kFieldDefinition;
import io.mapsmessaging.n2k.model.N2kFieldType;
import io.mapsmessaging.n2k.model.N2kMessageDefinition;
import java.util.Comparator;
import java.util.List;
import lombok.experimental.UtilityClass;

@UtilityClass
public class N2kJsonSchemaGenerator {

  public static JsonObject generateSchema(N2kMessageDefinition messageDefinition) {
    JsonObject schema = new JsonObject();

    schema.addProperty("$schema", "https://json-schema.org/draft/2020-12/schema");
    schema.addProperty("title", buildTitle(messageDefinition));
    schema.addProperty("type", "object");

    JsonObject properties = new JsonObject();

    JsonObject pgn = new JsonObject();
    pgn.addProperty("type", "integer");
    pgn.addProperty("const", messageDefinition.getPgn());
    properties.add("pgn", pgn);

    JsonObject decoded = new JsonObject();
    decoded.addProperty("type", "object");

    JsonObject decodedProperties = new JsonObject();

    List<N2kFieldDefinition> fields = messageDefinition.getFields().stream()
        .sorted(Comparator.comparingInt(N2kFieldDefinition::getOrder))
        .toList();

    for (N2kFieldDefinition field : fields) {
      if (!isSchemaField(field)) {
        continue;
      }

      String id = field.getId();
      if (id == null || id.isBlank()) {
        continue;
      }

      JsonObject prop = new JsonObject();
      prop.addProperty("type", "number");

      String description = buildFieldDescription(field);
      if (description != null) {
        prop.addProperty("description", description);
      }

      if (field.getRangeMin() != null) {
        prop.addProperty("minimum", field.getRangeMin());
      }
      if (field.getRangeMax() != null) {
        prop.addProperty("maximum", field.getRangeMax());
      }

      if (field.getResolution() > 0.0) {
        prop.addProperty("multipleOf", field.getResolution());
      }

      // Non-standard but useful metadata
      prop.addProperty("x-bitLength", field.getBitLength());
      prop.addProperty("x-bitOffset", field.getBitOffset());
      prop.addProperty("x-signed", field.isSigned());
      prop.addProperty("x-resolution", field.getResolution());
      prop.addProperty("x-offset", field.getOffset());

      if (field.getUnit() != null && !field.getUnit().isBlank()) {
        prop.addProperty("x-unit", field.getUnit());
      }
      if (field.getTypeInPdf() != null && !field.getTypeInPdf().isBlank()) {
        prop.addProperty("x-typeInPdf", field.getTypeInPdf());
      }
      prop.addProperty("x-fieldType", field.getFieldType().name());

      decodedProperties.add(id, prop);
    }

    decoded.add("properties", decodedProperties);
    decoded.addProperty("additionalProperties", false);

    properties.add("decoded", decoded);

    schema.add("properties", properties);

    JsonArray required = new JsonArray();
    required.add("pgn");
    required.add("decoded");
    schema.add("required", required);

    schema.addProperty("additionalProperties", false);

    return schema;
  }

  private static boolean isSchemaField(N2kFieldDefinition field) {
    if (field.getBitOffset() == null || field.getBitLength() == null) {
      return false;
    }

    N2kFieldType type = field.getFieldType();
    if (type == N2kFieldType.STRING_FIX ||
        type == N2kFieldType.STRING_LAU ||
        type == N2kFieldType.REPEAT_MARKER ||
        type == N2kFieldType.RESERVED) {
      return false;
    }

    return true;
  }

  private static String buildTitle(N2kMessageDefinition messageDefinition) {
    String id = messageDefinition.getId();
    String description = messageDefinition.getDescription();

    StringBuilder sb = new StringBuilder();
    sb.append("N2K PGN ").append(messageDefinition.getPgn());

    if (id != null && !id.isBlank()) {
      sb.append(" ").append(id);
    }
    if (description != null && !description.isBlank()) {
      sb.append(" - ").append(description);
    }

    return sb.toString();
  }

  private static String buildFieldDescription(N2kFieldDefinition field) {
    String name = field.getName();
    String unit = field.getUnit();

    if ((name == null || name.isBlank()) && (unit == null || unit.isBlank())) {
      return null;
    }

    if (unit == null || unit.isBlank()) {
      return name;
    }

    if (name == null || name.isBlank()) {
      return unit;
    }

    return name + " (" + unit + ")";
  }
}
