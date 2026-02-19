package com.obe.mapperstudio.task.model;

import java.util.Locale;

public enum TargetType {
    JSON_SCHEMA,
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
        if ("JSON_SCHEMA".equals(value) || "JSON".equals(value)) {
            return JSON_SCHEMA;
        }
        return looksLikeXml(targetSchema) ? XSD : JSON_SCHEMA;
    }

    public boolean isXmlType() {
        return this == XSD || this == XSD_WSDL;
    }

    public static boolean looksLikeXml(String input) {
        return input != null && input.trim().startsWith("<");
    }
}
