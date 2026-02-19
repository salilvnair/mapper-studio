package com.obe.mapperstudio.service.studio.path;

public class XmlPathFormatter extends AbstractPathFormatter implements PathFormatter {

    @Override
    public String formatPath(String sourcePath) {
        String normalized = normalize(sourcePath);
        return normalized.isBlank() ? "/" : "/" + normalized.replace('.', '/');
    }

    @Override
    public String leaf(String sourcePath) {
        return leafInternal(sourcePath);
    }
}
