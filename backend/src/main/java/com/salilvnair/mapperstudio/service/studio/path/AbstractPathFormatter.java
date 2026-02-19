package com.salilvnair.mapperstudio.service.studio.path;

abstract class AbstractPathFormatter {

    protected String normalize(String sourcePath) {
        String value = sourcePath == null ? "" : sourcePath.trim();
        return value.replaceAll("/+", ".").replaceAll("\\.+", ".").replaceAll("^\\.|\\.$", "");
    }

    protected String leafInternal(String sourcePath) {
        String normalized = normalize(sourcePath);
        if (normalized.isBlank()) {
            return "";
        }
        String[] parts = normalized.split("\\.");
        return parts[parts.length - 1];
    }
}
