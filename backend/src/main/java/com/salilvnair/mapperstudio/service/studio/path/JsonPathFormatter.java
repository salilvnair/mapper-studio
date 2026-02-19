package com.salilvnair.mapperstudio.service.studio.path;

public class JsonPathFormatter extends AbstractPathFormatter implements PathFormatter {

    @Override
    public String formatPath(String sourcePath) {
        String normalized = normalize(sourcePath);
        return normalized.isBlank() ? "$" : "$." + normalized;
    }

    @Override
    public String leaf(String sourcePath) {
        return leafInternal(sourcePath);
    }
}
