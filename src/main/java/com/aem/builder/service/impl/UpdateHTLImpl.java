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

        // Step 1: Extract existing fields with types
        Map<String, String> existingFields = extractExistingFields(content);

        // Step 2: Build field map from request
        Map<String, ComponentField> requestFields = buildFieldMap(request.getFields());

        // Step 3: Identify fields to remove
        Set<String> fieldsToRemove = new HashSet<>();
        for (String existingField : existingFields.keySet()) {
            if (!requestFields.containsKey(existingField)) {
                fieldsToRemove.add(existingField);
            } else {
                // If type changed, mark for removal
                String existingType = existingFields.get(existingField);
                String newType = requestFields.get(existingField).getFieldType();
                if (!existingType.equalsIgnoreCase(newType)) {
                    fieldsToRemove.add(existingField);
                }
            }
        }

        // Step 4: Remove obsolete or type-changed fields
        for (String field : fieldsToRemove) {
            String type = existingFields.get(field);
            System.out.println("type of the field......"+type);
            content = removeFieldHTL(content, field, type); // Pass type for multifield handling
            log.info("Removed field: {}", field);
        }

        // Step 5: Re-extract fields after removal
        existingFields = extractExistingFields(content);

        // Step 6: Add or update fields from request
        StringBuilder newFields = new StringBuilder();
        for (ComponentField field : request.getFields()) {
            boolean exists = existingFields.containsKey(field.getFieldName());
            boolean typeMatches = exists && existingFields.get(field.getFieldName()).equalsIgnoreCase(field.getFieldType());

            if (!exists || !typeMatches) {
                // Skip existing multifields without changes
                if ("multifield".equalsIgnoreCase(field.getFieldType()) && exists) {
                    log.info("Skipping existing multifield without changes: {}", field.getFieldName());
                    continue;
                }

                // Check if the field block is already present
                if (!isFieldBlockPresent(content, field.getFieldName(), field.getFieldType())) {
                    String fieldHTL = generateFieldHTL(field);
                    newFields.append(fieldHTL);
                    log.info("Inserted new field block: {}", field.getFieldName());
                } else {
                    log.info("Field block already present, skipping insertion: {}", field.getFieldName());
                }
            } else {
                log.info("Field already exists and unchanged: {}", field.getFieldName());
            }
        }

        // Step 7: Insert new fields if any
        if (newFields.length() > 0) {
            content = insertNewFields(content, newFields.toString());
            log.info("Inserted new fields.");
        }

        // Step 8: Write back the updated HTL
        Files.writeString(path, content);
        log.info("HTL update completed.");
    }


    private Map<String, String> extractExistingFields(String content) {
        Map<String, String> fields = new HashMap<>();

        Pattern blockPattern = Pattern.compile(
                "<p>.*?\\$\\{(?:model|item)\\.([a-zA-Z0-9_]+).*?\\}.*?</p>|" +
                        "<sly\\s+data-sly-test\\s*=\\s*\"\\$\\{(?:model|item)\\.([a-zA-Z0-9_]+).*?\\}\">",
                Pattern.DOTALL);

        Matcher matcher = blockPattern.matcher(content);
        while (matcher.find()) {
            String name = matcher.group(1) != null ? matcher.group(1) : matcher.group(2);
            if (name != null && !fields.containsKey(name)) {
                String type = detectFieldType(content, name);
                fields.put(name, type);
            }
        }

        return fields;
    }

//
//    private String detectFieldType(String content, String fieldName) {
//        if (content.contains("data-sly-list.item=\"${model." + fieldName + "}\""))
//            return "multifield";
//        if (content.contains("<input type=\"checkbox\"") && content.contains("${model." + fieldName + "}"))
//            return "checkbox";
//        if (content.contains("<img src=\"${model." + fieldName + "}\""))
//            return "fileupload";
//        if (content.contains("<a href=\"${model." + fieldName + "}\""))
//            return "pathfield";
//        return "textfield";
//    }
    private String detectFieldType(String content, String fieldName) {
        if (content.contains("data-sly-list.item=\"${model." + fieldName + "}\""))
            return "multifield";
        if (content.matches("(?s).*<input[^>]+type\\s*=\\s*\"checkbox\"[^>]*\\$\\{model\\." + Pattern.quote(fieldName) + "\\}.*"))
            return "checkbox";
        if (content.matches("(?s).*<img[^>]+src\\s*=\\s*\"\\$\\{model\\." + Pattern.quote(fieldName) + "\\}\".*"))
            return "fileupload";
        if (content.matches("(?s).*<a[^>]+href\\s*=\\s*\"\\$\\{model\\." + Pattern.quote(fieldName) + "\\}\".*"))
            return "pathfield";
        return "textfield";
    }

    private Map<String, ComponentField> buildFieldMap(List<ComponentField> fields) {
        Map<String, ComponentField> map = new HashMap<>();
        for (ComponentField field : fields) {
            map.put(field.getFieldName(), field);
            if ("multifield".equalsIgnoreCase(field.getFieldType()) && field.getNestedFields() != null) {
                map.putAll(buildFieldMap(field.getNestedFields()));
            }
        }
        return map;
    }

//    private String removeFieldHTL(String content, String fieldName) {
//        String regex = "<sly\\s+data-sly-test\\s*=\\s*\"\\$\\{(?:model|item)\\." + Pattern.quote(fieldName) + ".*?\">.*?</sly>";
//        content = Pattern.compile(regex, Pattern.DOTALL).matcher(content).replaceAll("");
//
//        String regexP = "<p>.*?\\$\\{(?:model|item)\\." + Pattern.quote(fieldName) + ".*?\\}.*?</p>";
//        content = Pattern.compile(regexP, Pattern.DOTALL).matcher(content).replaceAll("");
//
//        return content;
//    }

    private String insertNewFields(String content, String newFields) {
        String modelClass = extractSlingModelClass(content);
        if (modelClass == null) {
            throw new IllegalArgumentException("Model class not found in HTL.");
        }

        String marker = "<sly data-sly-use.model=\"" + modelClass + "\"/>\n" +
                "<sly data-sly-use.placeholderTemplate=\"core/wcm/components/commons/v1/templates.html\"/>\n" +
                "<sly data-sly-test.hasContent=\"${!model.empty}\">";

        int index = content.indexOf(marker);
        if (index == -1) {
            throw new IllegalArgumentException("Target block not found in HTL.");
        }
        index += marker.length();

        return content.substring(0, index) + "\n" + newFields + content.substring(index);
    }


    private String extractSlingModelClass(String content) {
        Pattern pattern = Pattern.compile("<sly\\s+data-sly-use\\.model=\"([^\"]+)\"");
        Matcher matcher = pattern.matcher(content);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    private String generateFieldHTL(ComponentField field) {
        StringBuilder sb = new StringBuilder();
        String fieldName = field.getFieldName();
        String label = field.getFieldLabel();

        switch (field.getFieldType().toLowerCase()) {
            case "fileupload":
                sb.append("  <p>").append(label)
                        .append(": <img src=\"${model.").append(fieldName)
                        .append("}\" alt=\"").append(label)
                        .append("\" style=\"max-width:100%; height:auto;\"/></p>\n");
                break;
            case "pathfield":
                sb.append("  <sly data-sly-test=\"${model.").append(fieldName).append("}\">\n")
                        .append("    <p>").append(label)
                        .append(": <a href=\"${model.").append(fieldName).append("}\">")
                        .append(label).append("</a></p>\n")
                        .append("  </sly>\n");
                break;
            case "checkbox":
                sb.append("  <sly data-sly-test=\"${model.").append(fieldName).append("}\" data-sly-unwrap>\n")
                        .append("    <p>").append(label)
                        .append(": <input type=\"checkbox\" disabled checked=\"checked\"/></p>\n")
                        .append("  </sly>\n");
                break;


            case "multifield":
                sb.append(generateMultifieldHTL(field));
                break;
            case "select":
                sb.append("  <sly data-sly-test=\"${model.").append(fieldName).append("}\">\n")
                        .append("    <p>").append(label)
                        .append(": ${model.").append(fieldName).append("}</p>\n")
                        .append("  </sly>\n");
                break;
            case "multiselect":
            case "tagfield":
                sb.append("  <sly data-sly-test=\"${model.").append(fieldName)
                        .append(" && model.").append(fieldName).append(".size > 0}\">\n")
                        .append("    <ul data-sly-list.item=\"${model.").append(fieldName).append("}\">\n")
                        .append("      <li>${item}</li>\n")
                        .append("    </ul>\n")
                        .append("  </sly>\n");
                break;
            default:
                sb.append("  <p>").append(label)
                        .append(": ${model.").append(fieldName)
                        .append(" @ context=\"html\"}</p>\n");
        }
        return sb.toString();
    }
    private String generateMultifieldHTL(ComponentField field) {
        StringBuilder sb = new StringBuilder();
        String fieldName = field.getFieldName();
        String label = field.getFieldLabel();

        sb.append("  <sly data-sly-test=\"${model.").append(fieldName)
                .append(" && model.").append(fieldName).append(".size > 0}\">\n")
                .append("    <ul data-sly-list.item=\"${model.").append(fieldName).append("}\">\n");

        for (ComponentField nested : field.getNestedFields()) {
            sb.append("      <sly data-sly-test=\"${item.").append(nested.getFieldName()).append("}\">\n")
                    .append("        <p>").append(nested.getFieldLabel()).append(": ");

            if ("fileupload".equalsIgnoreCase(nested.getFieldType())) {
                sb.append("<img src=\"${item.").append(nested.getFieldName()).append("}\" alt=\"")
                        .append(nested.getFieldLabel())
                        .append("\" style=\"max-width:100%; height:auto;\"/>");
            } else if ("pathfield".equalsIgnoreCase(nested.getFieldType())) {
                sb.append("<a href=\"${item.").append(nested.getFieldName()).append("}\">")
                        .append("${item.").append(nested.getFieldName()).append("}</a>");
            } else {
                sb.append("${item.").append(nested.getFieldName()).append("}");
            }

            sb.append("</p>\n")
                    .append("      </sly>\n");
        }

        sb.append("    </ul>\n")
                .append("  </sly>\n");

        return sb.toString();
    }
    private String removeFieldHTL(String content, String fieldName, String fieldType) {
        if ("multifield".equalsIgnoreCase(fieldType)) {
            // Remove entire multifield block
            String regex = "<sly\\s+data-sly-test\\s*=\\s*\"\\$\\{model\\." + Pattern.quote(fieldName) +
                    ".*?\">.*?</sly>";
            content = Pattern.compile(regex, Pattern.DOTALL).matcher(content).replaceAll("");

            String regexP = "<p>.*?\\$\\{model\\." + Pattern.quote(fieldName) + ".*?\\}.*?</p>";
            content = Pattern.compile(regexP, Pattern.DOTALL).matcher(content).replaceAll("");
        } else if (fieldName.contains(".")) {
            // Nested item inside multifield
            String nestedField = fieldName.split("\\.")[1];
            String regex = "<sly\\s+data-sly-test\\s*=\\s*\"\\$\\{item\\." + Pattern.quote(nestedField) +
                    ".*?\">.*?</sly>";
            content = Pattern.compile(regex, Pattern.DOTALL).matcher(content).replaceAll("");

            String regexP = "<p>.*?\\$\\{item\\." + Pattern.quote(nestedField) + ".*?\\}.*?</p>";
            content = Pattern.compile(regexP, Pattern.DOTALL).matcher(content).replaceAll("");
        } else {
            // Top-level field
            String regex = "<sly\\s+data-sly-test\\s*=\\s*\"\\$\\{(?:model|item)\\." + Pattern.quote(fieldName) +
                    ".*?\">.*?</sly>";
            content = Pattern.compile(regex, Pattern.DOTALL).matcher(content).replaceAll("");

            String regexP = "<p>.*?\\$\\{(?:model|item)\\." + Pattern.quote(fieldName) + ".*?\\}.*?</p>";
            content = Pattern.compile(regexP, Pattern.DOTALL).matcher(content).replaceAll("");
        }

        return content;
    }

    private boolean isFieldBlockPresent(String content, String fieldName, String fieldType) {
        if ("multifield".equalsIgnoreCase(fieldType)) {
            String regex = "<sly\\s+data-sly-test\\s*=\\s*\"\\$\\{model\\." + Pattern.quote(fieldName) +
                    ".*?\">|<p>.*?\\$\\{model\\." + Pattern.quote(fieldName) + ".*?\\}</p>";
            return Pattern.compile(regex, Pattern.DOTALL).matcher(content).find();
        } else if (fieldName.contains(".")) {
            String nestedField = fieldName.split("\\.")[1];
            String regex = "<sly\\s+data-sly-test\\s*=\\s*\"\\$\\{item\\." + Pattern.quote(nestedField) +
                    ".*?\">|<p>.*?\\$\\{item\\." + Pattern.quote(nestedField) + ".*?\\}</p>";
            return Pattern.compile(regex, Pattern.DOTALL).matcher(content).find();
        } else {
            String regex = "<sly\\s+data-sly-test\\s*=\\s*\"\\$\\{(?:model|item)\\." + Pattern.quote(fieldName) +
                    ".*?\">|<p>.*?\\$\\{(?:model|item)\\." + Pattern.quote(fieldName) + ".*?\\}</p>";
            return Pattern.compile(regex, Pattern.DOTALL).matcher(content).find();
        }
    }



}
