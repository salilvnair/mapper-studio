package com.salilvnair.mapperstudio.task.model;

public final class StudioSessionKeys {

    private StudioSessionKeys() {
    }

    public static final String STUDIO_MODE = "studio_mode";

    public static final String PROJECT_CODE = "projectCode";
    public static final String MAPPING_VERSION = "mappingVersion";
    public static final String SOURCE_TYPE = "sourceType";
    public static final String TARGET_TYPE = "targetType";

    public static final String SOURCE_SPEC = "sourceSpec";
    public static final String TARGET_SCHEMA = "targetSchema";
    public static final String TARGET_SCHEMA_JSON = "targetSchemaJson";
    public static final String TARGET_SCHEMA_XSD = "targetSchemaXsd";
    public static final String TARGET_SCHEMA_WSDL = "targetSchemaWsdl";
    public static final String TARGET_SCHEMA_XSD_NAME = "targetSchemaXsdName";
    public static final String TARGET_SCHEMA_WSDL_NAME = "targetSchemaWsdlName";
    public static final String TARGET_SCHEMA_XSD_LIST = "targetSchemaXsdList";

    public static final String PARSED_SOURCE_FIELDS = "parsed_source_fields";
    public static final String PARSED_TARGET_FIELDS = "parsed_target_fields";
    public static final String TARGET_TYPE_NORMALIZED = "target_type";

    public static final String MAPPING_SUGGESTIONS = "mapping_suggestions";
    public static final String VALIDATION_REPORT = "validation_report";
    public static final String MISSING_REQUIRED = "missing_required";
    public static final String TYPE_MISMATCH = "type_mismatch";
    public static final String DUPLICATE_TARGETS = "duplicate_targets";

    public static final String PARSE_STATUS = "parse_status";
    public static final String PARSE_RESULT = "parse_result";
    public static final String SUGGESTION_STATUS = "suggestion_status";
    public static final String VALIDATION_STATUS = "validation_status";
    public static final String PUBLISH_STATUS = "publish_status";
    public static final String PUBLISH_RESULT = "publish_result";

    public static final String STATUS_DONE = "DONE";
    public static final String STATUS_SKIPPED = "SKIPPED";

    public static final String STATE_AWAITING_CONFIRMATION = "AWAITING_CONFIRMATION";
    public static final String STATE_PUBLISHED = "PUBLISHED";

    public static final String DEFAULT_PROJECT_CODE = "MAPPER_DEMO_PROJECT";
    public static final String DEFAULT_MAPPING_VERSION = "1.0.0";
    public static final String DEFAULT_SOURCE_TYPE = "JSON";
    public static final String DEFAULT_SOURCE_SPEC = "{}";
    public static final String DEFAULT_TARGET_SCHEMA = "{}";
    public static final String DEFAULT_TARGET_SCHEMA_XSD_NAME = "target.xsd";
    public static final String DEFAULT_TARGET_SCHEMA_WSDL_NAME = "target.wsdl";
    public static final String DEFAULT_TARGET_TYPE = "JSON_SCHEMA";

    public static final String MODE_MAPPING_STUDIO = "MAPPING_STUDIO";
}
