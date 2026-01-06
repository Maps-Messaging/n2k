package io.mapsmessaging.n2k.schema;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.mapsmessaging.n2k.compile.N2kCompiledField;
import io.mapsmessaging.n2k.compile.N2kCompiledMessage;
import io.mapsmessaging.n2k.compile.N2kCompiledRegistry;
import io.mapsmessaging.n2k.model.N2kFieldType;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class N2kSchemaRegistry {

  private final N2kCompiledRegistry registry;

  public JsonObject getSchema(int pgn) {
    N2kCompiledMessage message = registry.getRequiredMessage(pgn);

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

      JsonObject fieldSchema = new JsonObject();

      if (field.getFieldType() == N2kFieldType.STRING_FIX || field.getFieldType() == N2kFieldType.STRING_LAU) {
        fieldSchema.addProperty("type", "string");
      } else {
        boolean integerLike = field.getResolution() == 1.0 && field.getOffset() == 0.0;
        fieldSchema.addProperty("type", integerLike ? "integer" : "number");
      }

      if (field.getUnit() != null) {
        fieldSchema.addProperty("unit", field.getUnit());
      }

      if (field.getName() != null) {
        fieldSchema.addProperty("description", field.getName());
      }

      if (field.getRangeMin() != null) {
        fieldSchema.addProperty("minimum", field.getRangeMin());
      }

      if (field.getRangeMax() != null) {
        fieldSchema.addProperty("maximum", field.getRangeMax());
      }

      decodedProperties.add(field.getId(), fieldSchema);
      required.add(field.getId());
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

    return root;
  }
}
