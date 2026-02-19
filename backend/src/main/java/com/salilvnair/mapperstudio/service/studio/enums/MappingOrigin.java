package com.salilvnair.mapperstudio.service.studio.enums;

public enum MappingOrigin {
    LLM_DERIVED,
    EDITED;

    public static String resolve(String rawOrigin, boolean manualOverride) {
        if (rawOrigin != null && !rawOrigin.trim().isBlank()) {
            return rawOrigin.trim();
        }
        return manualOverride ? EDITED.name() : LLM_DERIVED.name();
    }
}
