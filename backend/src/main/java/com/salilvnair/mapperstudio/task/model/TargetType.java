package com.salilvnair.mapperstudio.task.model;

import java.util.Locale;

public enum TargetType {
    JSON_SCHEMA,
    JSON,
    XML,
    XSD,
    XSD_WSDL;

    public static TargetType resolve(String rawType, String targetSchema) {
        String value = rawType == null ? "" : rawType.trim().toUpperCase(Locale.ROOT);
        if ("XSD+WSDL".equals(value) || "XSD_WSDL".equals(value)) {
            return XSD_WSDL;
        }
        if ("XSD".equals(value)) {
            return XSD;
        }
        if ("XML".equals(value)) {
            return XML;
        }
        if ("JSON_SCHEMA".equals(value)) {
            return JSON_SCHEMA;
        }
        if ("JSON".equals(value)) {
            return JSON;
        }

        if (looksLikeWsdl(targetSchema)) {
            return XSD_WSDL;
        }
        if (looksLikeXsd(targetSchema)) {
            return XSD;
        }
        if (looksLikeXml(targetSchema)) {
            return XML;
        }
        if (looksLikeJsonSchema(targetSchema)) {
            return JSON_SCHEMA;
        }
        return JSON;
    }

    public boolean isXmlType() {
        return this == XML || this == XSD || this == XSD_WSDL;
    }

    public boolean isJsonType() {
        return this == JSON || this == JSON_SCHEMA;
    }

    public static boolean looksLikeXml(String input) {
        return input != null && input.trim().startsWith("<");
    }

    public static boolean looksLikeXsd(String input) {
        String text = input == null ? "" : input.toLowerCase(Locale.ROOT);
        return text.contains("<xsd:schema") || text.contains("<xs:schema");
    }

    public static boolean looksLikeWsdl(String input) {
        String text = input == null ? "" : input.toLowerCase(Locale.ROOT);
        return text.contains("<wsdl:definitions") || text.contains("<definitions") && text.contains("schemas.xmlsoap.org/wsdl");
    }

    public static boolean looksLikeJsonSchema(String input) {
        String text = input == null ? "" : input.toLowerCase(Locale.ROOT);
        return text.contains("\"properties\"") || text.contains("\"$schema\"");
    }
}
