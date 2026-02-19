package com.obe.mapperstudio.service.studio.path;

public interface PathFormatter {
    String formatPath(String sourcePath);
    String leaf(String sourcePath);
}
