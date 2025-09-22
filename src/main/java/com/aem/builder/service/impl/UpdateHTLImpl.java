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
                return "<p>" + label + ": <img src=\"${" + context + "." + name +
                        "}\" alt=\"Image\" style=\"max-width:100%; height:auto;\"/></p>\n";

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
        // Extract <ul> content
        Pattern ulPattern = Pattern.compile("(<ul[^>]*>)(.*?)(</ul>)", Pattern.DOTALL);
        Matcher ulMatcher = ulPattern.matcher(block);

        if (!ulMatcher.find()) {
            return block; // fallback: no <ul> found
        }

        String ulStart = ulMatcher.group(1);
        String ulContent = ulMatcher.group(2);
        String ulEnd = ulMatcher.group(3);

        // Map existing nested fields -> their raw block
        Map<String, String> existingBlocks = new LinkedHashMap<>();
        Pattern fieldPattern = Pattern.compile("(<p>.*?\\$\\{item\\.([a-zA-Z0-9_]+).*?</p>|<sly.*?\\$\\{item\\.([a-zA-Z0-9_]+).*?</sly>)", Pattern.DOTALL);
        Matcher fieldMatcher = fieldPattern.matcher(ulContent);

        while (fieldMatcher.find()) {
            String raw = fieldMatcher.group(1);
            String name = fieldMatcher.group(2) != null ? fieldMatcher.group(2) : fieldMatcher.group(3);
            if (name != null) {
                existingBlocks.put(name, raw);
            }
        }

        StringBuilder newUlContent = new StringBuilder();

        // Rebuild in dialog order (request order)
        for (ComponentField nested : multifield.getNestedFields()) {
            String name = nested.getFieldName();
            String requestedType = nested.getFieldType().toLowerCase();

            if (existingBlocks.containsKey(name)) {
                String existingBlock = existingBlocks.get(name);

                // Infer existing type
                String existingType = inferType(existingBlock);

                if (existingType.equalsIgnoreCase(requestedType)) {
                    // Keep as-is
                    newUlContent.append(existingBlock).append("\n");
                } else {
                    // Replace in-place
                    newUlContent.append(buildNestedFieldHTL(nested));
                    log.info("Replaced nested field '{}' in multifield '{}' due to type change", name, multifield.getFieldName());
                }
            } else {
                // New field → add
                newUlContent.append(buildNestedFieldHTL(nested));
                log.info("Added new nested field '{}' in multifield '{}'", name, multifield.getFieldName());
            }
        }

        // Fields missing in request → automatically dropped

        // Rebuild final block
        return ulStart + "\n" + newUlContent + ulEnd;
    }

    private String inferType(String block) {
        block = block.toLowerCase();

        // --- Image / file ---
        if (block.contains("<img")) return "fileupload";

        // --- Pathfield / link ---
        if (block.contains("<a href")) return "pathfield";

        // --- Checkbox / switch ---
        if (block.contains("input type=\"checkbox\"")) return "checkbox";
        if (block.contains("switch") || block.contains("toggle")) return "switch";

        // --- Radio / select ---
        if (block.contains("input type=\"radio\"")) return "radiogroup";
        if (block.contains("select") || block.contains("option")) return "select";

        // --- Multi-select (list rendering) ---
        if (block.contains("data-sly-list") && block.contains("li")) return "multiselect";

        // --- Multifield (nested loop) ---
        if (block.contains("data-sly-list.item=\"${model.") && block.contains("</ul>")) return "multifield";

        // --- Richtext ---
        if (block.contains("data-sly-use.richtext") || block.contains("richtext") || block.contains("cq:richtext"))
            return "richtext";

        // --- Textarea ---
        if (block.contains("<textarea") || block.contains("text-area")) return "textarea";

        // --- Password ---
        if (block.contains("input type=\"password\"")) return "password";

        // --- Number field ---
        if (block.contains("input type=\"number\"") || block.contains("numberfield")) return "numberfield";

        // --- Date picker ---
        if (block.contains("datepicker") || block.contains("input type=\"date\"")) return "datepicker";

        // --- Color field ---
        if (block.contains("input type=\"color\"") || block.contains("colorfield")) return "colorfield";

        // --- Fallback ---
        return "text";
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
        sb.append("<sly data-sly-test=\"${model.").append(fieldName).append(" && model.").append(fieldName).
                append(".size > 0}\">\n")
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
        Pattern slyPattern = Pattern.compile(
                "<sly\\s+[^>]*data-sly-test\\s*=\\s*\"\\$\\{model\\.([a-zA-Z0-9_]+)(.*?)\\}\"", Pattern.DOTALL);
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