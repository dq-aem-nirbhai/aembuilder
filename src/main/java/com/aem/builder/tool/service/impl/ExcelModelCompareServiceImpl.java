package com.aem.builder.tool.service.impl;

import com.aem.builder.tool.service.ExcelModelCompareService;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ValueMap;
import org.osgi.service.component.annotations.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.util.*;
import java.util.stream.Collectors;

@Component(service = ExcelModelCompareService.class, immediate = true)
public class ExcelModelCompareServiceImpl implements ExcelModelCompareService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ExcelModelCompareServiceImpl.class);


    @Override
    public String compareExcelWithModel(ResourceResolver resolver, InputStream excelInputStream, String modelPath) {
        try {
            Set<String> excelColumns = getExcelColumns(excelInputStream);
            Set<String> modelFields = getModelFields(resolver, modelPath);

            Set<String> normalizedExcelCols = normalizeFields(excelColumns);
            Set<String> normalizedModelFields = normalizeFields(modelFields);

            // Check that all model fields exist in Excel columns

            boolean containsAll = normalizedModelFields.containsAll(normalizedExcelCols);

            LOGGER.info("normalizedExcelCols : {}",normalizedExcelCols);
            LOGGER.info("normalizedModelFields : {}",normalizedModelFields);
            Set<String> missingFields = new HashSet<>(normalizedExcelCols);
            missingFields.removeAll(normalizedModelFields);
            LOGGER.info("missingFields : {}",missingFields);
            if(missingFields.isEmpty()){
                return "Matched";
            }
            else{
                return "NotMatched";
            }

        } catch (Exception e) {
            LOGGER.error("Error comparing Excel with model", e);
            return "Error: " + e.getMessage();
        }
    }
    @Override
    public Set<String> getExcelColumns(InputStream inputStream) {

        Set<String> columns = new LinkedHashSet<>();
        try (Workbook workbook = WorkbookFactory.create(inputStream)) {
            Sheet sheet = workbook.getSheetAt(0);
            Row headerRow = sheet.getRow(0);

            if (headerRow != null) {
                headerRow.forEach(cell -> columns.add(cell.getStringCellValue().trim()));

            }
        } catch (Exception e) {
            LOGGER.error("Error reading Excel file", e);
        }
        return columns;
    }

    public Set<String> getModelFields(ResourceResolver resolver, String modelPath) {
        Set<String> fields = new LinkedHashSet<>();
        Resource modelResource = resolver.getResource(modelPath + "/jcr:content/model/cq:dialog/content/items");
        ///conf/charger/settings/dam/cfm/models/mymodel/jcr:content/model/cq:dialog/content/items/1760336409855
        if (modelResource == null) {
            LOGGER.warn("No model resource found at {}", modelPath);
            return fields;
        }

        for (Resource field : modelResource.getChildren()) {
            ValueMap vm = field.getValueMap();
            String name = vm.get("name", String.class);
            if (name != null) {
                fields.add(name.trim());
            }
        }

        List<String>modelfields= fields.stream().map(String::toLowerCase).collect(Collectors.toList());
        return new LinkedHashSet<>(modelfields);
    }

    private Set<String> normalizeFields(Set<String> fields) {
        List<String>normalfields=  fields.stream()
                .filter(f -> f != null && !f.trim().isEmpty())
                .map(f -> f
                        .trim()                       // remove leading/trailing spaces
                        .toLowerCase()                 // case-insensitive
                        .replaceAll("[^a-z0-9]", "")   // remove underscores, spaces, symbols
                )
                .collect(Collectors.toList());
        return new LinkedHashSet<>(normalfields);
    }



}
