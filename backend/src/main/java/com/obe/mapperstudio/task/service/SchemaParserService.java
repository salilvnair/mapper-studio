package com.obe.mapperstudio.task.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.obe.mapperstudio.task.model.TargetType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class SchemaParserService {

    private final ObjectMapper mapper;

    public List<Map<String, Object>> parseSourceFields(String sourceSpecText) {
        List<Map<String, Object>> fields = new ArrayList<>();
        try {
            String trimmed = sourceSpecText == null ? "" : sourceSpecText.trim();
            if (trimmed.startsWith("<")) {
                fields.addAll(parseXmlFields(trimmed));
            } else {
                Object root = mapper.readValue(sourceSpecText, Object.class);
                flattenSource(root, "", fields);
            }
        } catch (Exception ignored) {
            if (sourceSpecText != null && !sourceSpecText.isBlank()) {
                fields.add(Map.of(
                        "path", "sourceSpec",
                        "type", "string",
                        "description", "Raw source input"
                ));
            }
        }
        return fields;
    }

    public List<Map<String, Object>> parseTargetFields(
            String targetSchemaText,
            TargetType targetType,
            String targetSchemaXsd,
            String targetSchemaWsdl,
            String targetSchemaXsdName,
            String targetSchemaWsdlName,
            List<Map<String, Object>> targetSchemaXsdList
    ) {
        if (TargetType.XSD_WSDL == targetType) {
            List<Map<String, Object>> merged = new ArrayList<>();
            if (targetSchemaXsdList != null && !targetSchemaXsdList.isEmpty()) {
                for (Map<String, Object> artifact : targetSchemaXsdList) {
                    String content = asString(artifact.get("content")).trim();
                    if (content.isBlank()) {
                        continue;
                    }
                    String name = asString(artifact.get("name"), "target.xsd");
                    merged.addAll(parseXmlTargetFields(content, name, "XSD"));
                }
            } else if (targetSchemaXsd != null && !targetSchemaXsd.isBlank()) {
                merged.addAll(parseXmlTargetFields(targetSchemaXsd, targetSchemaXsdName, "XSD"));
            }
            if (targetSchemaWsdl != null && !targetSchemaWsdl.isBlank()) {
                merged.addAll(parseXmlTargetFields(targetSchemaWsdl, targetSchemaWsdlName, "WSDL"));
            }
            if (!merged.isEmpty()) {
                return dedupeTargetFieldsByPath(merged);
            }
        }

        if (targetType.isXmlType() || TargetType.looksLikeXml(targetSchemaText)) {
            return parseXmlTargetFields(targetSchemaText, targetSchemaXsdName, "XSD");
        }

        return parseJsonSchemaTargetFields(targetSchemaText);
    }

    public String resolveEffectiveTargetSchema(
            TargetType targetType,
            String targetSchema,
            String targetSchemaJson,
            String targetSchemaXsd,
            String targetSchemaWsdl
    ) {
        if (TargetType.JSON_SCHEMA == targetType && targetSchemaJson != null && !targetSchemaJson.isBlank()) {
            return targetSchemaJson;
        }
        if (TargetType.XSD == targetType && targetSchemaXsd != null && !targetSchemaXsd.isBlank()) {
            return targetSchemaXsd;
        }
        if (TargetType.XSD_WSDL == targetType) {
            if (targetSchemaXsd != null && !targetSchemaXsd.isBlank()) {
                return targetSchemaXsd;
            }
            if (targetSchemaWsdl != null && !targetSchemaWsdl.isBlank()) {
                return targetSchemaWsdl;
            }
        }
        return targetSchema;
    }

    public String toTargetSchemaPayload(TargetType targetType, String targetSchema, String targetSchemaXsd, String targetSchemaWsdl) {
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("targetType", targetType.name());
            if (TargetType.JSON_SCHEMA == targetType) {
                Object parsed = mapper.readValue(targetSchema, Object.class);
                payload.put("schema", parsed);
            } else if (TargetType.XSD_WSDL == targetType) {
                payload.put("xsdSchemaText", targetSchemaXsd == null ? "" : targetSchemaXsd);
                payload.put("wsdlText", targetSchemaWsdl == null ? "" : targetSchemaWsdl);
                payload.put("schemaText", targetSchema == null ? "" : targetSchema);
            } else {
                payload.put("schemaText", targetSchema);
            }
            return mapper.writeValueAsString(payload);
        } catch (Exception ignored) {
            return "{\"targetType\":\"" + targetType.name() + "\",\"schemaText\":" + quoteJson(targetSchema) + "}";
        }
    }

    private List<Map<String, Object>> parseXmlFields(String xmlText) {
        List<Map<String, Object>> fields = new ArrayList<>();
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(false);
            Document doc = factory.newDocumentBuilder().parse(new InputSource(new StringReader(xmlText)));
            Element root = doc.getDocumentElement();
            if (root != null) {
                flattenXmlElement(root, root.getTagName(), fields);
            }
        } catch (Exception ignored) {
        }
        return fields;
    }

    private void flattenXmlElement(Element element, String path, List<Map<String, Object>> out) {
        NodeList children = element.getChildNodes();
        boolean hasElementChildren = false;
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                hasElementChildren = true;
                Element childEl = (Element) child;
                String childPath = path + "." + childEl.getTagName();
                flattenXmlElement(childEl, childPath, out);
            }
        }

        if (!hasElementChildren) {
            String value = element.getTextContent() == null ? "" : element.getTextContent().trim();
            if (!value.isBlank()) {
                out.add(Map.of(
                        "path", path,
                        "type", "string",
                        "description", "Extracted from source XML"
                ));
            }
        }
    }

    private void flattenSource(Object node, String path, List<Map<String, Object>> out) {
        if (node instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                String key = String.valueOf(entry.getKey());
                String childPath = path.isBlank() ? key : path + "." + key;
                flattenSource(entry.getValue(), childPath, out);
            }
            return;
        }
        if (node instanceof List<?> list) {
            for (int i = 0; i < list.size(); i++) {
                String childPath = path + "[" + i + "]";
                flattenSource(list.get(i), childPath, out);
            }
            return;
        }

        String type = inferType(node);
        out.add(Map.of(
                "path", path.isBlank() ? "root" : path,
                "type", type,
                "description", "Extracted from source JSON"
        ));
    }

    private List<Map<String, Object>> dedupeTargetFieldsByPath(List<Map<String, Object>> rows) {
        Map<String, Map<String, Object>> index = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            String path = String.valueOf(row.getOrDefault("path", ""));
            if (path.isBlank()) {
                continue;
            }
            index.putIfAbsent(path, row);
        }
        return new ArrayList<>(index.values());
    }

    private List<Map<String, Object>> parseJsonSchemaTargetFields(String targetSchemaText) {
        List<Map<String, Object>> fields = new ArrayList<>();
        try {
            Map<?, ?> schema = mapper.readValue(targetSchemaText, Map.class);
            Set<String> required = new LinkedHashSet<>();
            Object requiredRaw = schema.get("required");
            if (requiredRaw instanceof List<?> list) {
                for (Object item : list) {
                    required.add(String.valueOf(item));
                }
            }
            Object propsRaw = schema.get("properties");
            if (propsRaw instanceof Map<?, ?> props) {
                for (Map.Entry<?, ?> entry : props.entrySet()) {
                    String targetPath = String.valueOf(entry.getKey());
                    String type = "string";
                    if (entry.getValue() instanceof Map<?, ?> typeMap) {
                        Object t = typeMap.get("type");
                        if (t != null) {
                            type = String.valueOf(t);
                        }
                    }
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("path", targetPath);
                    row.put("type", type);
                    row.put("required", required.contains(targetPath));
                    fields.add(row);
                }
            }
        } catch (Exception ignored) {
        }
        return fields;
    }

    private List<Map<String, Object>> parseXmlTargetFields(String xmlSchemaText, String artifactName, String artifactType) {
        List<Map<String, Object>> fields = new ArrayList<>();
        if (xmlSchemaText == null || xmlSchemaText.isBlank()) {
            return fields;
        }
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            Document doc = factory.newDocumentBuilder().parse(new InputSource(new StringReader(xmlSchemaText)));
            NodeList all = doc.getElementsByTagName("*");
            Set<String> seenPaths = new LinkedHashSet<>();
            for (int i = 0; i < all.getLength(); i++) {
                Node node = all.item(i);
                if (!(node instanceof Element el)) {
                    continue;
                }
                String localName = el.getLocalName() != null ? el.getLocalName() : el.getTagName();
                if (!"element".equalsIgnoreCase(localName) && !"attribute".equalsIgnoreCase(localName)) {
                    continue;
                }

                String name = el.getAttribute("name");
                if (name == null || name.isBlank()) {
                    continue;
                }
                String rawType = el.getAttribute("type");
                if ("element".equalsIgnoreCase(localName) && shouldSkipXmlWrapperTarget(name, rawType, el)) {
                    continue;
                }
                String path = "attribute".equalsIgnoreCase(localName) ? "@" + name : name;
                if (!seenPaths.add(path)) {
                    continue;
                }

                String type = normalizeXmlType(rawType);
                boolean required = !"0".equals(el.getAttribute("minOccurs")) && !"optional".equalsIgnoreCase(el.getAttribute("use"));

                Map<String, Object> row = new LinkedHashMap<>();
                row.put("path", path);
                row.put("type", type);
                row.put("required", required);
                row.put("targetArtifactName", artifactName == null || artifactName.isBlank() ? "target.xsd" : artifactName);
                row.put("targetArtifactType", artifactType == null || artifactType.isBlank() ? "XSD" : artifactType);
                fields.add(row);
            }
        } catch (Exception ignored) {
        }
        return fields;
    }

    private boolean shouldSkipXmlWrapperTarget(String name, String rawType, Element elementNode) {
        if (name == null || name.isBlank()) {
            return true;
        }
        String loweredName = name.toLowerCase(Locale.ROOT);
        String type = rawType == null ? "" : rawType.trim().toLowerCase(Locale.ROOT);

        if (loweredName.endsWith("request") || loweredName.endsWith("response")) {
            return true;
        }
        if (!type.isBlank() && type.contains(":") && !type.startsWith("xsd:") && !type.startsWith("xs:")) {
            return true;
        }
        NodeList children = elementNode.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            Element el = (Element) child;
            String local = el.getLocalName() != null ? el.getLocalName() : el.getTagName();
            if ("complextype".equalsIgnoreCase(local) || "sequence".equalsIgnoreCase(local)) {
                return true;
            }
        }
        return false;
    }

    private String normalizeXmlType(String rawType) {
        if (rawType == null || rawType.isBlank()) {
            return "string";
        }
        String t = rawType.trim().toLowerCase(Locale.ROOT);
        int idx = t.indexOf(':');
        String base = idx >= 0 ? t.substring(idx + 1) : t;
        return switch (base) {
            case "int", "integer", "long", "short", "decimal", "float", "double" -> "number";
            case "boolean" -> "boolean";
            case "date", "datetime", "time" -> "string";
            default -> "string";
        };
    }

    private String quoteJson(String value) {
        try {
            return mapper.writeValueAsString(value == null ? "" : value);
        } catch (Exception ignored) {
            return "\"\"";
        }
    }

    private String inferType(Object node) {
        if (node == null) {
            return "null";
        }
        if (node instanceof Number) {
            return "number";
        }
        if (node instanceof Boolean) {
            return "boolean";
        }
        return "string";
    }

    private String asString(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private String asString(Object value, String defaultValue) {
        String str = asString(value).trim();
        return str.isBlank() ? defaultValue : str;
    }
}
