package com.obe.mapperstudio.service.studio.enums;

public enum PathType {
    JSON_PATH,
    XML_PATH;

    public static PathType from(String value) {
        if (value != null && value.trim().equalsIgnoreCase("XML_PATH")) {
            return XML_PATH;
        }
        return JSON_PATH;
    }
}
