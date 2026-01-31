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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.mapsmessaging.n2k.model.N2kFieldDefinition;
import io.mapsmessaging.n2k.model.N2kFieldType;
import io.mapsmessaging.n2k.model.N2kMessageDefinition;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class N2kJsonSchemaGeneratorTest {

  @Test
  void generateSchema_basics_includesSchemaTitleTypeRequiredAndNoAdditionalProperties() {
    N2kMessageDefinition messageDefinition = mock(N2kMessageDefinition.class);
    when(messageDefinition.getPgn()).thenReturn(129025);
    when(messageDefinition.getId()).thenReturn("POSITION_RAPID");
    when(messageDefinition.getDescription()).thenReturn("Position, Rapid Update");
    when(messageDefinition.getFields()).thenReturn(List.of());

    JsonObject schema = N2kJsonSchemaGenerator.generateSchema(messageDefinition);

    assertEquals("https://json-schema.org/draft/2020-12/schema", schema.get("$schema").getAsString());
    assertEquals("object", schema.get("type").getAsString());
    assertEquals("N2K PGN 129025 POSITION_RAPID - Position, Rapid Update", schema.get("title").getAsString());
    assertFalse(schema.get("additionalProperties").getAsBoolean());

    JsonArray required = schema.getAsJsonArray("required");
    assertNotNull(required);
    assertEquals(2, required.size());
    assertEquals("pgn", required.get(0).getAsString());
    assertEquals("decoded", required.get(1).getAsString());

    JsonObject properties = schema.getAsJsonObject("properties");
    assertNotNull(properties);

    JsonObject pgn = properties.getAsJsonObject("pgn");
    assertNotNull(pgn);
    assertEquals("integer", pgn.get("type").getAsString());
    assertEquals(129025, pgn.get("const").getAsInt());

    JsonObject decoded = properties.getAsJsonObject("decoded");
    assertNotNull(decoded);
    assertEquals("object", decoded.get("type").getAsString());
    assertFalse(decoded.get("additionalProperties").getAsBoolean());

    JsonObject decodedProperties = decoded.getAsJsonObject("properties");
    assertNotNull(decodedProperties);
    assertTrue(decodedProperties.entrySet().isEmpty());
  }

  @Test
  void generateSchema_includesValidNumericField_withDescriptionRangeMultipleOfAndMetadata() {
    N2kFieldDefinition field = mockField(
        10,
        "waterTemp",
        "Water Temperature",
        "K",
        16,
        32,
        true,
        0.1,
        273.15,
        0.0,
        400.0,
        "Temperature (Kelvin)",
        N2kFieldType.FLOAT
    );

    N2kMessageDefinition messageDefinition = mock(N2kMessageDefinition.class);
    when(messageDefinition.getPgn()).thenReturn(130310);
    when(messageDefinition.getId()).thenReturn("ENV_PARAM");
    when(messageDefinition.getDescription()).thenReturn("Environmental Parameters");
    when(messageDefinition.getFields()).thenReturn(List.of(field));

    JsonObject schema = N2kJsonSchemaGenerator.generateSchema(messageDefinition);

    JsonObject decodedProperties = schema
        .getAsJsonObject("properties")
        .getAsJsonObject("decoded")
        .getAsJsonObject("properties");

    JsonObject prop = decodedProperties.getAsJsonObject("waterTemp");
    assertNotNull(prop);

    assertEquals("number", prop.get("type").getAsString());
    assertEquals("Water Temperature (K)", prop.get("description").getAsString());
    assertEquals(0.0, prop.get("minimum").getAsDouble(), 0.0000001);
    assertEquals(400.0, prop.get("maximum").getAsDouble(), 0.0000001);
    assertEquals(0.1, prop.get("multipleOf").getAsDouble(), 0.0000001);

    assertEquals(16, prop.get("x-bitLength").getAsInt());
    assertEquals(32, prop.get("x-bitOffset").getAsInt());
    assertTrue(prop.get("x-signed").getAsBoolean());
    assertEquals(0.1, prop.get("x-resolution").getAsDouble(), 0.0000001);
    assertEquals(273.15, prop.get("x-offset").getAsDouble(), 0.0000001);

    assertEquals("K", prop.get("x-unit").getAsString());
    assertEquals("Temperature (Kelvin)", prop.get("x-typeInPdf").getAsString());
    assertEquals("FLOAT", prop.get("x-fieldType").getAsString());
  }

  @Test
  void generateSchema_filtersOutNonSchemaFieldTypes_andLeavesOnlyValidFields() {
    List<N2kFieldDefinition> fields = new ArrayList<>();

    fields.add(mockField(
        1,
        "validNumber",
        "Valid Number",
        "",
        8,
        0,
        false,
        0.0,
        0.0,
        null,
        null,
        null,
        N2kFieldType.NUMBER
    ));

    fields.add(mockField(
        2,
        "validFloat",
        "Valid Float",
        "",
        8,
        8,
        false,
        0.0,
        0.0,
        null,
        null,
        null,
        N2kFieldType.FLOAT
    ));

    fields.add(mockField(
        3,
        "validLookup",
        "Valid Lookup",
        "",
        8,
        16,
        false,
        0.0,
        0.0,
        null,
        null,
        null,
        N2kFieldType.LOOKUP
    ));

    fields.add(mockField(
        4,
        "fixedString",
        "Fixed String",
        "",
        8,
        24,
        false,
        0.0,
        0.0,
        null,
        null,
        null,
        N2kFieldType.STRING_FIX
    ));

    fields.add(mockField(
        5,
        "lauString",
        "LAU String",
        "",
        8,
        32,
        false,
        0.0,
        0.0,
        null,
        null,
        null,
        N2kFieldType.STRING_LAU
    ));

    fields.add(mockField(
        6,
        "repeatMarker",
        "Repeat Marker",
        "",
        8,
        40,
        false,
        0.0,
        0.0,
        null,
        null,
        null,
        N2kFieldType.REPEAT_MARKER
    ));

    fields.add(mockField(
        7,
        "reserved",
        "Reserved",
        "",
        8,
        48,
        false,
        0.0,
        0.0,
        null,
        null,
        null,
        N2kFieldType.RESERVED
    ));

    N2kMessageDefinition messageDefinition = mock(N2kMessageDefinition.class);
    when(messageDefinition.getPgn()).thenReturn(123456);
    when(messageDefinition.getId()).thenReturn("");
    when(messageDefinition.getDescription()).thenReturn("");
    when(messageDefinition.getFields()).thenReturn(fields);

    JsonObject schema = N2kJsonSchemaGenerator.generateSchema(messageDefinition);

    JsonObject decodedProperties = schema
        .getAsJsonObject("properties")
        .getAsJsonObject("decoded")
        .getAsJsonObject("properties");

    assertTrue(decodedProperties.has("validNumber"));
    assertTrue(decodedProperties.has("validFloat"));
    assertTrue(decodedProperties.has("validLookup"));

    assertFalse(decodedProperties.has("fixedString"));
    assertFalse(decodedProperties.has("lauString"));
    assertFalse(decodedProperties.has("repeatMarker"));
    assertFalse(decodedProperties.has("reserved"));

    assertEquals(3, decodedProperties.entrySet().size());
  }

  @Test
  void generateSchema_sortsFieldsByOrder_inInsertionOrderOfDecodedProperties() {
    N2kFieldDefinition later = mockField(
        20,
        "later",
        "Later",
        "",
        8,
        8,
        false,
        0.0,
        0.0,
        null,
        null,
        null,
        N2kFieldType.NUMBER
    );

    N2kFieldDefinition earlier = mockField(
        10,
        "earlier",
        "Earlier",
        "",
        8,
        0,
        false,
        0.0,
        0.0,
        null,
        null,
        null,
        N2kFieldType.NUMBER
    );

    N2kMessageDefinition messageDefinition = mock(N2kMessageDefinition.class);
    when(messageDefinition.getPgn()).thenReturn(999);
    when(messageDefinition.getId()).thenReturn(null);
    when(messageDefinition.getDescription()).thenReturn(null);
    when(messageDefinition.getFields()).thenReturn(List.of(later, earlier));

    JsonObject schema = N2kJsonSchemaGenerator.generateSchema(messageDefinition);

    JsonObject decodedProperties = schema
        .getAsJsonObject("properties")
        .getAsJsonObject("decoded")
        .getAsJsonObject("properties");

    Iterator<Map.Entry<String, JsonElement>> iterator = decodedProperties.entrySet().iterator();
    assertTrue(iterator.hasNext());
    assertEquals("earlier", iterator.next().getKey());

    assertTrue(iterator.hasNext());
    assertEquals("later", iterator.next().getKey());

    assertFalse(iterator.hasNext());
  }

  @Test
  void generateSchema_skipsFieldsWithBlankId_orMissingBitOffsetOrBitLength() {
    N2kFieldDefinition blankId = mock(N2kFieldDefinition.class);
    when(blankId.getOrder()).thenReturn(1);
    when(blankId.getId()).thenReturn("   ");
    when(blankId.getBitOffset()).thenReturn(0);
    when(blankId.getBitLength()).thenReturn(8);
    when(blankId.getFieldType()).thenReturn(N2kFieldType.NUMBER);

    N2kFieldDefinition missingOffset = mock(N2kFieldDefinition.class);
    when(missingOffset.getOrder()).thenReturn(2);
    when(missingOffset.getId()).thenReturn("missingOffset");
    when(missingOffset.getBitOffset()).thenReturn(null);
    when(missingOffset.getBitLength()).thenReturn(8);
    when(missingOffset.getFieldType()).thenReturn(N2kFieldType.NUMBER);

    N2kFieldDefinition missingLength = mock(N2kFieldDefinition.class);
    when(missingLength.getOrder()).thenReturn(3);
    when(missingLength.getId()).thenReturn("missingLength");
    when(missingLength.getBitOffset()).thenReturn(0);
    when(missingLength.getBitLength()).thenReturn(null);
    when(missingLength.getFieldType()).thenReturn(N2kFieldType.NUMBER);

    N2kFieldDefinition valid = mockField(
        4,
        "ok",
        "Ok",
        "",
        8,
        0,
        false,
        0.0,
        0.0,
        null,
        null,
        null,
        N2kFieldType.NUMBER
    );

    N2kMessageDefinition messageDefinition = mock(N2kMessageDefinition.class);
    when(messageDefinition.getPgn()).thenReturn(321);
    when(messageDefinition.getId()).thenReturn("X");
    when(messageDefinition.getDescription()).thenReturn("Y");
    when(messageDefinition.getFields()).thenReturn(List.of(blankId, missingOffset, missingLength, valid));

    JsonObject schema = N2kJsonSchemaGenerator.generateSchema(messageDefinition);

    JsonObject decodedProperties = schema
        .getAsJsonObject("properties")
        .getAsJsonObject("decoded")
        .getAsJsonObject("properties");

    assertFalse(decodedProperties.has("missingOffset"));
    assertFalse(decodedProperties.has("missingLength"));
    assertTrue(decodedProperties.has("ok"));
    assertEquals(1, decodedProperties.entrySet().size());
  }

  @Test
  void generateSchema_descriptionLogic_nameUnitVariants() {
    N2kFieldDefinition nameOnly = mockField(
        1,
        "nameOnly",
        "Speed",
        "",
        8,
        0,
        false,
        0.0,
        0.0,
        null,
        null,
        null,
        N2kFieldType.NUMBER
    );

    N2kFieldDefinition unitOnly = mockField(
        2,
        "unitOnly",
        "",
        "m/s",
        8,
        8,
        false,
        0.0,
        0.0,
        null,
        null,
        null,
        N2kFieldType.NUMBER
    );

    N2kFieldDefinition neither = mockField(
        3,
        "neither",
        "",
        "",
        8,
        16,
        false,
        0.0,
        0.0,
        null,
        null,
        null,
        N2kFieldType.NUMBER
    );

    N2kMessageDefinition messageDefinition = mock(N2kMessageDefinition.class);
    when(messageDefinition.getPgn()).thenReturn(1);
    when(messageDefinition.getId()).thenReturn("");
    when(messageDefinition.getDescription()).thenReturn("");
    when(messageDefinition.getFields()).thenReturn(List.of(nameOnly, unitOnly, neither));

    JsonObject schema = N2kJsonSchemaGenerator.generateSchema(messageDefinition);

    JsonObject decodedProperties = schema
        .getAsJsonObject("properties")
        .getAsJsonObject("decoded")
        .getAsJsonObject("properties");

    JsonObject nameOnlyProp = decodedProperties.getAsJsonObject("nameOnly");
    assertNotNull(nameOnlyProp);
    assertEquals("Speed", nameOnlyProp.get("description").getAsString());

    JsonObject unitOnlyProp = decodedProperties.getAsJsonObject("unitOnly");
    assertNotNull(unitOnlyProp);
    assertEquals("m/s", unitOnlyProp.get("description").getAsString());

    JsonObject neitherProp = decodedProperties.getAsJsonObject("neither");
    assertNotNull(neitherProp);
    assertFalse(neitherProp.has("description"));
  }

  private static N2kFieldDefinition mockField(
      int order,
      String id,
      String name,
      String unit,
      Integer bitLength,
      Integer bitOffset,
      boolean signed,
      double resolution,
      double offset,
      Double rangeMin,
      Double rangeMax,
      String typeInPdf,
      N2kFieldType fieldType
  ) {
    N2kFieldDefinition field = mock(N2kFieldDefinition.class);

    when(field.getOrder()).thenReturn(order);
    when(field.getId()).thenReturn(id);
    when(field.getName()).thenReturn(name);
    when(field.getUnit()).thenReturn(unit);

    when(field.getBitLength()).thenReturn(bitLength);
    when(field.getBitOffset()).thenReturn(bitOffset);
    when(field.isSigned()).thenReturn(signed);

    when(field.getResolution()).thenReturn(resolution);
    when(field.getOffset()).thenReturn(offset);

    when(field.getRangeMin()).thenReturn(rangeMin);
    when(field.getRangeMax()).thenReturn(rangeMax);

    when(field.getTypeInPdf()).thenReturn(typeInPdf);
    when(field.getFieldType()).thenReturn(fieldType);

    return field;
  }
}
