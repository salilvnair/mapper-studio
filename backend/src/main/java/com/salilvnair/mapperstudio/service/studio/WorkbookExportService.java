package com.salilvnair.mapperstudio.service.studio;

import com.salilvnair.mapperstudio.api.dto.MappingExportRequest;
import com.salilvnair.mapperstudio.api.dto.MappingExportRow;
import com.salilvnair.mapperstudio.service.studio.enums.MappingOrigin;
import com.salilvnair.mapperstudio.service.studio.enums.PathType;
import com.salilvnair.mapperstudio.service.studio.path.PathFormatter;
import com.salilvnair.mapperstudio.service.studio.path.PathFormatterFactory;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
@RequiredArgsConstructor
public class WorkbookExportService {

    private final PathFormatterFactory pathFormatterFactory;

    public byte[] buildWorkbook(MappingExportRequest request) throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            CellStyle headerStyle = workbook.createCellStyle();
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerStyle.setFont(headerFont);

            List<MappingExportRow> rows = request.mappings() == null ? List.of() : request.mappings();
            PathFormatter formatter = pathFormatterFactory.forType(PathType.from(request.pathType()));

            XSSFSheet primarySheet = workbook.createSheet("SourceTarget");
            Row h1 = primarySheet.createRow(0);
            String[] primaryHeaders = new String[]{"Source", "Target", "Path", "Path Type"};
            for (int i = 0; i < primaryHeaders.length; i++) {
                Cell c = h1.createCell(i);
                c.setCellValue(primaryHeaders[i]);
                c.setCellStyle(headerStyle);
            }

            int rowIndex = 1;
            for (MappingExportRow row : rows) {
                if (!Boolean.TRUE.equals(row.selected())) {
                    continue;
                }
                Row dataRow = primarySheet.createRow(rowIndex++);
                dataRow.createCell(0).setCellValue(formatter.leaf(row.sourcePath()));
                dataRow.createCell(1).setCellValue(safeText(row.targetPath(), ""));
                dataRow.createCell(2).setCellValue(formatter.formatPath(row.sourcePath()));
                dataRow.createCell(3).setCellValue(safeText(request.pathType(), PathType.JSON_PATH.name()));
            }

            XSSFSheet mappingSheet = workbook.createSheet("Mappings");
            Row h2 = mappingSheet.createRow(0);
            String[] mappingHeaders = new String[]{
                    "Selected", "Source Path", "Target Path", "Transform", "Confidence", "Origin", "Reason", "Notes",
                    "Manual Override", "Artifact Name", "Artifact Type"
            };
            for (int i = 0; i < mappingHeaders.length; i++) {
                Cell c = h2.createCell(i);
                c.setCellValue(mappingHeaders[i]);
                c.setCellStyle(headerStyle);
            }

            int mappingRowIndex = 1;
            for (MappingExportRow row : rows) {
                Row dataRow = mappingSheet.createRow(mappingRowIndex++);
                dataRow.createCell(0).setCellValue(Boolean.TRUE.equals(row.selected()) ? "Y" : "N");
                dataRow.createCell(1).setCellValue(safeText(row.sourcePath(), ""));
                dataRow.createCell(2).setCellValue(safeText(row.targetPath(), ""));
                dataRow.createCell(3).setCellValue(safeText(row.transformType(), "DIRECT"));
                dataRow.createCell(4).setCellValue(row.confidence() == null ? 0d : row.confidence());
                dataRow.createCell(5).setCellValue(MappingOrigin.resolve(row.mappingOrigin(), Boolean.TRUE.equals(row.manualOverride())));
                dataRow.createCell(6).setCellValue(safeText(row.reason(), ""));
                dataRow.createCell(7).setCellValue(safeText(row.notes(), ""));
                dataRow.createCell(8).setCellValue(Boolean.TRUE.equals(row.manualOverride()) ? "Y" : "N");
                dataRow.createCell(9).setCellValue(safeText(row.targetArtifactName(), ""));
                dataRow.createCell(10).setCellValue(safeText(row.targetArtifactType(), ""));
            }

            XSSFSheet summarySheet = workbook.createSheet("Summary");
            Row s0 = summarySheet.createRow(0);
            s0.createCell(0).setCellValue("Project Code");
            s0.createCell(1).setCellValue(request.projectCode());
            Row s1 = summarySheet.createRow(1);
            s1.createCell(0).setCellValue("Version");
            s1.createCell(1).setCellValue(request.mappingVersion());
            Row s2 = summarySheet.createRow(2);
            s2.createCell(0).setCellValue("Source Type");
            s2.createCell(1).setCellValue(request.sourceType());
            Row s3 = summarySheet.createRow(3);
            s3.createCell(0).setCellValue("Target Type");
            s3.createCell(1).setCellValue(request.targetType());
            Row s4 = summarySheet.createRow(4);
            s4.createCell(0).setCellValue("Path Type");
            s4.createCell(1).setCellValue(request.pathType());
            Row s5 = summarySheet.createRow(5);
            s5.createCell(0).setCellValue("Exported At");
            s5.createCell(1).setCellValue(OffsetDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));

            int selectedCount = (int) rows.stream().filter(m -> Boolean.TRUE.equals(m.selected())).count();
            Row s6 = summarySheet.createRow(6);
            s6.createCell(0).setCellValue("Selected Mappings");
            s6.createCell(1).setCellValue(selectedCount);

            for (int i = 0; i < primaryHeaders.length; i++) {
                primarySheet.autoSizeColumn(i);
            }
            for (int i = 0; i < mappingHeaders.length; i++) {
                mappingSheet.autoSizeColumn(i);
            }
            summarySheet.autoSizeColumn(0);
            summarySheet.autoSizeColumn(1);

            workbook.write(out);
            return out.toByteArray();
        }
    }

    private String safeText(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value.trim();
    }
}
