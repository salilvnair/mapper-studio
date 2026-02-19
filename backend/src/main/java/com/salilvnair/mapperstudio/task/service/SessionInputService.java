package com.salilvnair.mapperstudio.task.service;

import com.github.salilvnair.convengine.engine.session.EngineSession;
import com.salilvnair.mapperstudio.task.model.StudioSessionKeys;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class SessionInputService {

    public void clearTransientStatuses(EngineSession session) {
        if (session.getInputParams() == null) {
            return;
        }
        session.getInputParams().remove(StudioSessionKeys.PARSE_STATUS);
        session.getInputParams().remove(StudioSessionKeys.PARSE_RESULT);
        session.getInputParams().remove(StudioSessionKeys.SUGGESTION_STATUS);
        session.getInputParams().remove(StudioSessionKeys.MAPPING_SUGGESTIONS);
        session.getInputParams().remove(StudioSessionKeys.VALIDATION_STATUS);
        session.getInputParams().remove(StudioSessionKeys.VALIDATION_REPORT);
        session.getInputParams().remove(StudioSessionKeys.MISSING_REQUIRED);
        session.getInputParams().remove(StudioSessionKeys.TYPE_MISMATCH);
        session.getInputParams().remove(StudioSessionKeys.DUPLICATE_TARGETS);
        session.getInputParams().remove(StudioSessionKeys.PUBLISH_STATUS);
        session.getInputParams().remove(StudioSessionKeys.PUBLISH_RESULT);
    }

    public String readSessionValue(EngineSession session, String key, String defaultValue) {
        Object inputValue = session.getInputParams() == null ? null : session.getInputParams().get(key);
        if (inputValue != null) {
            String v = String.valueOf(inputValue);
            if (!v.isBlank()) {
                return v;
            }
        }
        Map<String, Object> ctx = session.contextDict();
        Object ctxValue = ctx == null ? null : ctx.get(key);
        if (ctxValue != null) {
            String v = String.valueOf(ctxValue);
            if (!v.isBlank()) {
                return v;
            }
        }
        return defaultValue;
    }

    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> readArtifactList(EngineSession session, String key) {
        Object inputValue = session.getInputParams() == null ? null : session.getInputParams().get(key);
        Object raw = inputValue;
        if (raw == null) {
            Map<String, Object> ctx = session.contextDict();
            raw = ctx == null ? null : ctx.get(key);
        }
        if (!(raw instanceof List<?> list)) {
            return List.of();
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof Map<?, ?> map) {
                Map<String, Object> row = new LinkedHashMap<>();
                map.forEach((k, v) -> row.put(String.valueOf(k), v));
                out.add(row);
            }
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> readFieldList(Object raw) {
        if (raw instanceof List<?> list) {
            List<Map<String, Object>> out = new ArrayList<>();
            for (Object item : list) {
                if (item instanceof Map<?, ?> map) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    map.forEach((k, v) -> row.put(String.valueOf(k), v));
                    out.add(row);
                }
            }
            return out;
        }
        return List.of();
    }
}
