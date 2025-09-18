package com.aem.builder.service.impl;

import com.aem.builder.model.DTO.ComponentField;
import com.aem.builder.model.DTO.ComponentRequest;
import com.aem.builder.service.UpdateHTL;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Service
public class UpdateHTLImpl implements UpdateHTL {

    @Override
    public void updateHTLFromRequest(ComponentRequest request, String filePath) throws IOException {
        Path path = Path.of(filePath);
        String content = Files.readString(path);

        // --- Step 1: Extract existing top-level fields ---
        Set<String> existingFields = extractTopLevelFields(content);

        // --- Step 2: Determine fields to remove ---
        List<String> requestFieldNames = request.getFields().stream()
                .map(ComponentField::getFieldName)
                .collect(Collectors.toList());
        Set<String> fieldsToRemove = new HashSet<>(existingFields);
        fieldsToRemove.removeAll(requestFieldNames);


        // --- Step 2.1: Handle type changes ---
        Map<String, String> existingFieldTypes = extractFieldsWithTypes(content);

        Set<String> fieldsWithTypeChanges = new HashSet<>();
        for (ComponentField field : request.getFields()) {
            String existingType = existingFieldTypes.get(field.getFieldName());
            if (existingType != null && !existingType.equalsIgnoreCase(field.getFieldType())) {
                fieldsWithTypeChanges.add(field.getFieldName());
            }
        }

// Add fields with type changes to removal set
        fieldsToRemove.addAll(fieldsWithTypeChanges);


        // --- Step 3: Remove obsolete fields ---
        for (String field : fieldsToRemove) {
            content = removeFieldBlock(content, field);
            log.info("Removed obsolete field: {}", field);
        }

        // --- Step 4: Update or insert normal fields ---
        existingFields = extractTopLevelFields(content); // refresh after removal
        StringBuilder normalFieldsToInsert = new StringBuilder();

        List<ComponentField> multifields = new ArrayList<>();

        for (ComponentField field : request.getFields()) {
            if ("multifield".equalsIgnoreCase(field.getFieldType())) {
                multifields.add(field); // handle separately
            } else {
                if (!existingFields.contains(field.getFieldName()) || fieldsToRemove.contains(field.getFieldName())) {
                    normalFieldsToInsert.append(buildNormalFieldHTL(field));
                }
            }
        }

        // --- Step 5: Insert normal fields before closing </sly> ---
        if (normalFieldsToInsert.length() > 0) {
            int insertIndex = content.lastIndexOf("</sly>");
            if (insertIndex == -1) {
                throw new IllegalArgumentException("Closing </sly> tag not found in HTL.");
            }
            content = content.substring(0, insertIndex) + "\n" + normalFieldsToInsert + content.substring(insertIndex);
        }

        // --- Step 6: Update multifields in-place ---
        for (ComponentField mf : multifields) {
            content = updateMultifieldInPlace(content, mf);
        }

        // --- Step 7: Write updated content ---
        Files.writeString(path, content);
        log.info("HTL updated successfully: {}", filePath);
    }

    // ==================== Helper Methods ====================

    private Set<String> extractTopLevelFields(String content) {
        Set<String> fields = new HashSet<>();
        Pattern pattern = Pattern.compile("\\$\\{model\\.([a-zA-Z0-9_]+)");
        Matcher matcher = pattern.matcher(content);
        while (matcher.find()) {
            fields.add(matcher.group(1));
        }
        return fields;
    }

    private String removeFieldBlock(String content, String fieldName) {
        // Remove sly blocks
        String regexSly = "<sly\\s+[^>]*data-sly-test\\s*=\\s*\"\\$\\{model\\."
                + Pattern.quote(fieldName) + "(?:\\s*&&[^}]*)?}\"\\s*>.*?</sly>\\s*";
        content = Pattern.compile(regexSly, Pattern.DOTALL).matcher(content).replaceAll("");

        // Remove paragraphs
        String regexP = "<p>.*?\\$\\{model\\." + Pattern.quote(fieldName) + ".*?\\}.*?</p>\\s*";
        content = Pattern.compile(regexP, Pattern.DOTALL).matcher(content).replaceAll("");

        return content;
    }
    private String buildFieldHTL(ComponentField field, String context) {
        String name = field.getFieldName();
        String label = field.getFieldLabel();

        switch (field.getFieldType().toLowerCase()) {
            case "fileupload":
                return "<p>" + label + ": <img src=\"${" + context + "." + name + "}\" alt=\"Image\" style=\"max-width:100%; height:auto;\"/></p>\n";

            case "pathfield":
                return "<sly data-sly-test=\"${" + context + "." + name + "}\">\n" +
                        "  <p>" + label + ": <a href=\"${" + context + "." + name + "}\">" + label + "</a></p>\n" +
                        "</sly>\n";

            case "checkbox":
            case "switch":
                return "<sly data-sly-test=\"${" + context + "." + name + "}\">\n" +
                        "  <p>" + label + ": <input type=\"checkbox\" disabled checked=\"checked\"/></p>\n" +
                        "</sly>\n";

            case "radiogroup":
            case "select":
                return "<sly data-sly-test=\"${" + context + "." + name + "}\">\n" +
                        "  <p>" + label + ": ${" + context + "." + name + "}</p>\n" +
                        "</sly>\n";

            case "multiselect":
            case "tagfield":
                return "<sly data-sly-test=\"${" + context + "." + name + " && " + context + "." + name + ".size > 0}\">\n" +
                        "  <ul data-sly-list.item=\"${" + context + "." + name + "}\">\n" +
                        "    <li>${item}</li>\n" +
                        "  </ul>\n" +
                        "</sly>\n";

            default:
                return "<p>" + label + ": ${" + context + "." + name + " @ context=\"html\"}</p>\n";
        }
    }

    // For top-level fields (normal ones)
    private String buildNormalFieldHTL(ComponentField field) {
        return buildFieldHTL(field, "model");
    }

    // For nested fields (inside multifield)
    private String buildNestedFieldHTL(ComponentField nested) {
        return buildFieldHTL(nested, "item");
    }


    /**
     * Updates multifield in-place. Only adds missing nested fields, removes obsolete nested fields.
     */
    private String updateMultifieldInPlace(String content, ComponentField multifield) {
        String fieldName = multifield.getFieldName();

        // Regex to find multifield <ul> block
        String regexMultifield = "<sly\\s+[^>]*data-sly-test\\s*=\\s*\"\\$\\{model\\." + Pattern.quote(fieldName) +
                "(?:.*?)\\}\".*?>.*?<ul[^>]*>.*?</ul>.*?</sly>";
        Pattern pattern = Pattern.compile(regexMultifield, Pattern.DOTALL);
        Matcher matcher = pattern.matcher(content);

        if (matcher.find()) {
            String block = matcher.group(0);
            String updatedBlock = mergeNestedFields(block, multifield);
            content = matcher.replaceFirst(Matcher.quoteReplacement(updatedBlock));
            log.info("Updated multifield block in-place: {}", fieldName);
        } else {
            // Multifield doesn't exist yet: append
            String newBlock = buildFullMultifieldHTL(multifield);
            int insertIndex = content.lastIndexOf("</sly>");
            if (insertIndex == -1) insertIndex = content.length();
            content = content.substring(0, insertIndex) + "\n" + newBlock + content.substring(insertIndex);
            log.info("Inserted new multifield block: {}", fieldName);
        }

        return content;
    }

    /**
     * Merge nested fields: remove deleted fields, append new fields
     */
    private String mergeNestedFields(String block, ComponentField multifield) {
        // --- 1. Extract existing nested fields and types ---
        Map<String, String> existingNestedFields = new HashMap<>();
        Pattern nestedPattern = Pattern.compile("\\$\\{(?:item(?:_[a-zA-Z0-9_]+)?\\.)?([a-zA-Z0-9_]+)");
        Matcher matcher = nestedPattern.matcher(block);
        while (matcher.find()) {
            String nestedName = matcher.group(1);

            // Heuristic to guess type from surrounding content
            int start = matcher.end();
            int end = Math.min(block.length(), start + 200);
            String snippet = block.substring(start, end);
            String type = "text";
            if (snippet.contains("<img")) {
                type = "fileupload";
            } else if (snippet.contains("<a href")) {
                type = "pathfield";
            }
            existingNestedFields.put(nestedName, type);
        }

        // --- 2. Prepare request nested fields with types ---
        Map<String, String> requestNestedFields = new HashMap<>();
        for (ComponentField nested : multifield.getNestedFields()) {
            requestNestedFields.put(nested.getFieldName(), nested.getFieldType().toLowerCase());
        }

        // --- 3. Remove obsolete or type-changed nested fields ---
        for (Map.Entry<String, String> entry : existingNestedFields.entrySet()) {
            String nestedName = entry.getKey();
            String existingType = entry.getValue();

            if (!requestNestedFields.containsKey(nestedName)) {
                // Field removed
                block = removeNestedField(block, nestedName);
                log.info("Removed obsolete nested field '{}' from multifield '{}'", nestedName, multifield.getFieldName());
            } else if (!existingType.equalsIgnoreCase(requestNestedFields.get(nestedName))) {
                // Type changed → remove and re-add
                block = removeNestedField(block, nestedName);
                log.info("Removed nested field '{}' due to type change in multifield '{}'", nestedName, multifield.getFieldName());
            }
        }

        // --- 4. Append new or type-changed nested fields ---
        StringBuilder toAppend = new StringBuilder();
        for (ComponentField nested : multifield.getNestedFields()) {
            String name = nested.getFieldName();
            if (!existingNestedFields.containsKey(name) ||
                    !existingNestedFields.get(name).equalsIgnoreCase(nested.getFieldType())) {
                toAppend.append(buildNestedFieldHTL(nested));
                log.info("Added/updated nested field '{}' in multifield '{}'", name, multifield.getFieldName());
            }
        }

        // --- 5. Insert at end of <ul> ---
        int ulEnd = block.lastIndexOf("</ul>");
        if (toAppend.length() > 0 && ulEnd != -1) {
            block = block.substring(0, ulEnd) + toAppend + block.substring(ulEnd);
        }

        return block;
    }


    private String removeNestedField(String block, String nestedName) {
        // Remove sly blocks
        String regexSly = "<sly\\s+[^>]*data-sly-test\\s*=\\s*\"\\$\\{(?:item(?:_[a-zA-Z0-9_]+)?\\.)"
                + Pattern.quote(nestedName) + "(?:.*?)\\}\"\\s*>.*?</sly>\\s*";
        block = Pattern.compile(regexSly, Pattern.DOTALL).matcher(block).replaceAll("");

        // Remove paragraphs
        String regexP = "<p>.*?\\$\\{(?:item(?:_[a-zA-Z0-9_]+)?\\.)" + Pattern.quote(nestedName) + ".*?\\}.*?</p>\\s*";
        block = Pattern.compile(regexP, Pattern.DOTALL).matcher(block).replaceAll("");

        return block;
    }


    private String buildFullMultifieldHTL(ComponentField multifield) {
        String fieldName = multifield.getFieldName();
        StringBuilder sb = new StringBuilder();
        sb.append("<sly data-sly-test=\"${model.").append(fieldName).append(" && model.").append(fieldName).append(".size > 0}\">\n")
                .append("  <ul data-sly-list.item=\"${model.").append(fieldName).append("}\">\n");
        for (ComponentField nested : multifield.getNestedFields()) {
            sb.append(buildNestedFieldHTL(nested));
        }
        sb.append("  </ul>\n</sly>\n");
        return sb.toString();
    }


    private Map<String, String> extractFieldsWithTypes(String content) {
        Map<String, String> fieldTypes = new HashMap<>();

        // Pattern to find data-sly-test blocks
        Pattern slyPattern = Pattern.compile("<sly\\s+[^>]*data-sly-test\\s*=\\s*\"\\$\\{model\\.([a-zA-Z0-9_]+)(.*?)\\}\"", Pattern.DOTALL);
        Matcher slyMatcher = slyPattern.matcher(content);
        while (slyMatcher.find()) {
            String field = slyMatcher.group(1);
            String extra = slyMatcher.group(2);
            String type = "unknown";

            // Check context by looking at surrounding content
            int start = slyMatcher.end();
            int end = Math.min(content.length(), start + 200); // scan next 200 chars
            String snippet = content.substring(start, end);

            if (snippet.contains("<img")) {
                type = "fileupload";
            } else if (snippet.contains("<a href")) {
                type = "pathfield";
            } else if (snippet.contains("input type=\"checkbox\"")) {
                type = "checkbox";
            } else if (snippet.contains("data-sly-list")) {
                type = "multiselect"; // or tagfield
            } else {
                type = "text";
            }
            fieldTypes.put(field, type);
        }

        // Also scan for <p> tags directly referencing model.field
        Pattern pPattern = Pattern.compile("<p>.*?\\$\\{model\\.([a-zA-Z0-9_]+).*?\\}.*?</p>", Pattern.DOTALL);
        Matcher pMatcher = pPattern.matcher(content);
        while (pMatcher.find()) {
            String field = pMatcher.group(1);
            if (!fieldTypes.containsKey(field)) {
                fieldTypes.put(field, "text");
            }
        }

        return fieldTypes;
    }

}