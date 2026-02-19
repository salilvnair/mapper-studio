package com.obe.mapperstudio.api;

import com.obe.mapperstudio.api.dto.DbInitResponse;
import com.obe.mapperstudio.api.dto.DbInitStatusResponse;
import com.obe.mapperstudio.api.dto.MappingConfirmResponse;
import com.obe.mapperstudio.api.dto.MappingExportRequest;
import com.obe.mapperstudio.api.dto.MappingSaveResponse;
import com.obe.mapperstudio.api.dto.StudioMessageRequest;
import com.obe.mapperstudio.api.dto.StudioMessageResponse;
import com.obe.mapperstudio.service.studio.DbInitializationService;
import com.obe.mapperstudio.service.studio.MappingManagementService;
import com.obe.mapperstudio.service.studio.StudioConversationService;
import com.obe.mapperstudio.service.studio.WorkbookExportService;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/studio")
@Validated
@RequiredArgsConstructor
public class StudioController {

    private final StudioConversationService studioConversationService;
    private final MappingManagementService mappingManagementService;
    private final WorkbookExportService workbookExportService;
    private final DbInitializationService dbInitializationService;

    @PostMapping("/message")
    public StudioMessageResponse message(@RequestBody @NotNull StudioMessageRequest request) {
        return studioConversationService.process(request);
    }

    @PostMapping("/mappings/save")
    public MappingSaveResponse saveMappings(@RequestBody @NotNull MappingExportRequest request) {
        return mappingManagementService.saveMappings(request);
    }

    @PostMapping("/mappings/confirm")
    public MappingConfirmResponse confirmMappings(@RequestBody @NotNull MappingExportRequest request) {
        return mappingManagementService.confirmMappings(request);
    }

    @PostMapping("/mappings/export")
    public ResponseEntity<byte[]> exportMappings(@RequestBody @NotNull MappingExportRequest request) throws java.io.IOException {
        mappingManagementService.validateExportAllowed(request.projectCode(), request.mappingVersion());

        byte[] bytes = workbookExportService.buildWorkbook(request);
        String safeProject = request.projectCode().replaceAll("[^a-zA-Z0-9_-]", "_");
        String safeVersion = request.mappingVersion().replaceAll("[^a-zA-Z0-9._-]", "_");
        String fileName = safeProject + "_" + safeVersion + "_mappings.xlsx";

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\"")
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(bytes);
    }

    @PostMapping("/admin/db/init")
    public DbInitResponse initializeDb() {
        return dbInitializationService.initialize();
    }

    @GetMapping("/admin/db/status")
    public DbInitStatusResponse dbStatus() {
        return dbInitializationService.status();
    }
}
