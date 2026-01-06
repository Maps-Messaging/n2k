package io.mapsmessaging.n2k.parser;

import io.mapsmessaging.n2k.model.N2kFieldDefinition;
import io.mapsmessaging.n2k.model.N2kFieldType;
import io.mapsmessaging.n2k.model.N2kMessageDefinition;
import io.mapsmessaging.n2k.model.N2kMessageLengthType;
import lombok.experimental.UtilityClass;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@UtilityClass
public class N2kXmlDialectParser {

  public static List<N2kMessageDefinition> parseFromClasspath(String resourcePath) throws Exception {
    String normalizedPath = normalizeResourcePath(resourcePath);

    ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
    if (classLoader == null) {
      classLoader = N2kXmlDialectParser.class.getClassLoader();
    }

    try (InputStream inputStream = classLoader.getResourceAsStream(normalizedPath)) {
      if (inputStream == null) {
        throw new IllegalArgumentException("N2K XML resource not found on classpath: " + normalizedPath);
      }
      return parse(inputStream);
    }
  }

  public static List<N2kMessageDefinition> parseFromFile(Path filePath) throws Exception {
    try (InputStream inputStream = Files.newInputStream(filePath)) {
      return parse(inputStream);
    }
  }

  public static List<N2kMessageDefinition> parse(InputStream inputStream) throws Exception {
    DocumentBuilderFactory documentBuilderFactory = DocumentBuilderFactory.newInstance();
    documentBuilderFactory.setNamespaceAware(false);
    documentBuilderFactory.setIgnoringComments(true);

    Document document = documentBuilderFactory.newDocumentBuilder().parse(inputStream);
    Element rootElement = document.getDocumentElement();

    List<Element> pgnInfoElements = findElementsByTagName(rootElement, "PGNInfo");

    List<N2kMessageDefinition> messageDefinitions = new ArrayList<>(pgnInfoElements.size());
    for (Element pgnElement : pgnInfoElements) {
      messageDefinitions.add(parsePgnInfo(pgnElement));
    }

    messageDefinitions.sort(Comparator.comparingInt(N2kMessageDefinition::getPgn));
    return List.copyOf(messageDefinitions);
  }

  private static N2kMessageDefinition parsePgnInfo(Element pgnElement) {
    int pgn = parseIntChildRequired(pgnElement, "PGN", "PGNInfo");

    String id = parseStringChild(pgnElement, "Id", null);
    String description = parseStringChild(pgnElement, "Description", null);

    int priority = parseIntChildOptional(pgnElement, "Priority", 0);
    String type = parseStringChild(pgnElement, "Type", null);

    boolean complete = parseBooleanChild(pgnElement, "Complete", false);

    N2kMessageLengthParseResult lengthResult = parseLength(pgnElement, pgn);

    Element fieldsElement = firstDirectChildElement(pgnElement, "Fields");
    List<N2kFieldDefinition> fieldDefinitions = new ArrayList<>();
    if (fieldsElement != null) {
      List<Element> fieldElements = directChildElements(fieldsElement, "Field");
      for (Element fieldElement : fieldElements) {
        fieldDefinitions.add(parseField(fieldElement, pgn));
      }
    }

    fieldDefinitions.sort(Comparator.comparingInt(N2kFieldDefinition::getOrder));

    return new N2kMessageDefinition(
        pgn,
        id,
        description,
        priority,
        type,
        complete,
        lengthResult.getLengthType(),
        lengthResult.getFixedLengthBytes(),
        List.copyOf(fieldDefinitions)
    );
  }

  private static N2kMessageLengthParseResult parseLength(Element pgnElement, int pgn) {
    String text = parseStringChild(pgnElement, "Length", null);
    if (text == null) {
      return new N2kMessageLengthParseResult(N2kMessageLengthType.VARIABLE, null);
    }

    String trimmed = text.trim();
    if (trimmed.equalsIgnoreCase("Variable")) {
      return new N2kMessageLengthParseResult(N2kMessageLengthType.VARIABLE, null);
    }

    try {
      int lengthBytes = Integer.parseInt(trimmed);
      return new N2kMessageLengthParseResult(N2kMessageLengthType.FIXED, lengthBytes);
    }
    catch (NumberFormatException exception) {
      throw new IllegalArgumentException("Invalid <Length> '" + trimmed + "' for PGN " + pgn, exception);
    }
  }

  private static N2kFieldDefinition parseField(Element fieldElement, int pgn) {
    int order = parseIntChildOptional(fieldElement, "Order", 0);

    String id = normalizeFieldId(parseStringChild(fieldElement, "Id", null));
    String name = parseStringChild(fieldElement, "Name", null);

    String typeInPdf = parseStringChild(fieldElement, "TypeInPdf", null);

    String fieldTypeText = parseStringChild(fieldElement, "FieldType", null);
    N2kFieldType fieldType = resolveFieldType(fieldTypeText, typeInPdf, name);

    Integer bitOffset = parseNullableIntChild(fieldElement, "BitOffset");
    Integer bitLength = parseNullableIntChild(fieldElement, "BitLength");
    Integer bitStart = parseNullableIntChild(fieldElement, "BitStart");

    boolean signed = parseBooleanChild(fieldElement, "Signed", false);

    double resolution = parseDoubleChildOptional(fieldElement, "Resolution", 1.0);
    double offset = parseDoubleChildOptional(fieldElement, "Offset", 0.0);

    Double rangeMin = parseNullableDoubleChild(fieldElement, "RangeMin");
    Double rangeMax = parseNullableDoubleChild(fieldElement, "RangeMax");

    String unit = parseStringChild(fieldElement, "Unit", null);

    return new N2kFieldDefinition(
        order,
        id,
        name,
        bitOffset,
        bitLength,
        bitStart,
        signed,
        fieldType,
        resolution,
        offset,
        rangeMin,
        rangeMax,
        unit,
        typeInPdf
    );
  }

  private static String normalizeFieldId(String id) {
    if (id == null) {
      return null;
    }

    String trimmed = id.trim();
    if (trimmed.isEmpty()) {
      return null;
    }

    char first = trimmed.charAt(0);
    if (!Character.isUpperCase(first)) {
      return trimmed;
    }

    return Character.toLowerCase(first) + trimmed.substring(1);
  }

  private static N2kFieldType resolveFieldType(String fieldTypeText, String typeInPdf, String name) {
    if (fieldTypeText != null) {
      String normalized = fieldTypeText.trim().toUpperCase();
      try {
        return N2kFieldType.valueOf(normalized);
      }
      catch (IllegalArgumentException ignored) {
      }
    }

    boolean looksLikeRepeatMarker = false;

    if (typeInPdf != null && typeInPdf.equalsIgnoreCase("Undefined")) {
      looksLikeRepeatMarker = true;
    }

    if (name != null && name.toLowerCase().contains("repeat")) {
      looksLikeRepeatMarker = true;
    }

    if (looksLikeRepeatMarker) {
      return N2kFieldType.REPEAT_MARKER;
    }

    return N2kFieldType.NUMBER;
  }

  private static Integer parseNullableIntChild(Element parent, String tagName) {
    String text = parseStringChild(parent, tagName, null);
    if (text == null) {
      return null;
    }

    try {
      return Integer.parseInt(text.trim());
    }
    catch (NumberFormatException exception) {
      throw new IllegalArgumentException("Invalid <" + tagName + "> '" + text + "'", exception);
    }
  }

  private static int parseIntChildRequired(Element parent, String tagName, String context) {
    String text = parseStringChild(parent, tagName, null);
    if (text == null) {
      throw new IllegalArgumentException("Missing <" + tagName + "> in " + context);
    }

    try {
      return Integer.parseInt(text.trim());
    }
    catch (NumberFormatException exception) {
      throw new IllegalArgumentException("Invalid <" + tagName + "> '" + text + "' in " + context, exception);
    }
  }

  private static int parseIntChildOptional(Element parent, String tagName, int defaultValue) {
    String text = parseStringChild(parent, tagName, null);
    if (text == null) {
      return defaultValue;
    }

    try {
      return Integer.parseInt(text.trim());
    }
    catch (NumberFormatException exception) {
      throw new IllegalArgumentException("Invalid <" + tagName + "> '" + text + "'", exception);
    }
  }

  private static double parseDoubleChildOptional(Element parent, String tagName, double defaultValue) {
    String text = parseStringChild(parent, tagName, null);
    if (text == null) {
      return defaultValue;
    }

    try {
      return Double.parseDouble(text.trim());
    }
    catch (NumberFormatException exception) {
      throw new IllegalArgumentException("Invalid <" + tagName + "> '" + text + "'", exception);
    }
  }

  private static Double parseNullableDoubleChild(Element parent, String tagName) {
    String text = parseStringChild(parent, tagName, null);
    if (text == null) {
      return null;
    }

    try {
      return Double.parseDouble(text.trim());
    }
    catch (NumberFormatException exception) {
      throw new IllegalArgumentException("Invalid <" + tagName + "> '" + text + "'", exception);
    }
  }

  private static boolean parseBooleanChild(Element parent, String tagName, boolean defaultValue) {
    String text = parseStringChild(parent, tagName, null);
    if (text == null) {
      return defaultValue;
    }
    return Boolean.parseBoolean(text.trim());
  }

  private static String parseStringChild(Element parent, String tagName, String defaultValue) {
    Element childElement = firstDirectChildElement(parent, tagName);
    if (childElement == null) {
      return defaultValue;
    }

    String text = childElement.getTextContent();
    if (text == null) {
      return defaultValue;
    }

    String trimmed = text.trim();
    if (trimmed.isEmpty()) {
      return defaultValue;
    }
    return trimmed;
  }

  private static Element firstDirectChildElement(Element parent, String tagName) {
    Node childNode = parent.getFirstChild();
    while (childNode != null) {
      if (childNode.getNodeType() == Node.ELEMENT_NODE) {
        Element childElement = (Element) childNode;
        if (tagName.equals(childElement.getTagName())) {
          return childElement;
        }
      }
      childNode = childNode.getNextSibling();
    }
    return null;
  }

  private static List<Element> directChildElements(Element parent, String tagName) {
    ArrayList<Element> results = new ArrayList<>();
    Node childNode = parent.getFirstChild();
    while (childNode != null) {
      if (childNode.getNodeType() == Node.ELEMENT_NODE) {
        Element childElement = (Element) childNode;
        if (tagName.equals(childElement.getTagName())) {
          results.add(childElement);
        }
      }
      childNode = childNode.getNextSibling();
    }
    return results;
  }

  private static List<Element> findElementsByTagName(Element rootElement, String tagName) {
    ArrayList<Element> results = new ArrayList<>();
    collectElementsByTagName(rootElement, tagName, results);
    return results;
  }

  private static void collectElementsByTagName(Element element, String tagName, List<Element> results) {
    if (tagName.equals(element.getTagName())) {
      results.add(element);
    }

    Node childNode = element.getFirstChild();
    while (childNode != null) {
      if (childNode.getNodeType() == Node.ELEMENT_NODE) {
        collectElementsByTagName((Element) childNode, tagName, results);
      }
      childNode = childNode.getNextSibling();
    }
  }

  private static String normalizeResourcePath(String resourcePath) {
    if (resourcePath == null || resourcePath.isBlank()) {
      throw new IllegalArgumentException("Resource path must not be blank");
    }
    String trimmed = resourcePath.trim();
    if (trimmed.startsWith("/")) {
      return trimmed.substring(1);
    }
    return trimmed;
  }
}
