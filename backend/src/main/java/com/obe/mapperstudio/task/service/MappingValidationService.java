package com.obe.mapperstudio.task.service;

import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class MappingValidationService {

    public Map<String, Object> defaultValidationReport() {
        Map<String, Object> validation = new LinkedHashMap<>();
        validation.put("missingRequired", Collections.emptyList());
        validation.put("typeMismatch", Collections.emptyList());
        validation.put("duplicateMappings", Collections.emptyList());
        validation.put("readyToPublish", true);
        return validation;
    }
}
