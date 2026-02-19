package com.obe.mapperstudio.service.studio.path;

import com.obe.mapperstudio.service.studio.enums.PathType;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.Map;

@Component
public class PathFormatterFactory {

    private final Map<PathType, PathFormatter> formatters = new EnumMap<>(PathType.class);

    public PathFormatterFactory() {
        formatters.put(PathType.JSON_PATH, new JsonPathFormatter());
        formatters.put(PathType.XML_PATH, new XmlPathFormatter());
    }

    public PathFormatter forType(PathType type) {
        return formatters.getOrDefault(type, formatters.get(PathType.JSON_PATH));
    }
}
