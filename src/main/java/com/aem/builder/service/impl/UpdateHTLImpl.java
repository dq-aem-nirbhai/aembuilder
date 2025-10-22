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
    public Map<String, String> fieldsWithChangedTypes(ComponentRequest newRequest,
                                                      Map<String, String> existingFieldsWithType) {
        Map<String, String> typeChangedFields = new LinkedHashMap<>();

        for (ComponentField field : newRequest.getFields()) {
            String fieldName = field.getFieldName();
            String newType = field.getFieldType();

            String existingType = existingFieldsWithType.get(fieldName);
            // If field exists and type is different, add to map
            if (existingType != null && !existingType.equalsIgnoreCase(newType)) {
                typeChangedFields.put(fieldName, newType);
            }
        }

        return typeChangedFields;
    }


    @Override
    public void updateHTLFromRequest(ComponentRequest request, String filePath,String projectName,
                                     ComponentRequest oldRequest) throws IOException {
        Path path = Path.of(filePath);
        String content = Files.readString(path);

        log.info("[DEBUG] HTL content loaded:\n{}", content);
        List<String>fieldsfromrequest=new ArrayList<>();

        for(ComponentField field : request.getFields()){
            fieldsfromrequest.add(field.getFieldName());
        }
        log.info("[updateHTLFromRequest] filed from request :{}",fieldsfromrequest);
        Set<String> requestFieldSet = new HashSet<>(fieldsfromrequest);
        // --- Step 1: Extract existing top-level fields ---
        Set<String> existingFields = extractTopLevelFields(content);
        log.info("[updateHTLFromRequest] existingFields : {} ",existingFields);
        // Find fields to remove (existing in HTL but not in request)
        Set<String> obsoleteFields = new HashSet<>(existingFields);
        obsoleteFields.removeAll(requestFieldSet);
        log.info("[updateHTLFromRequest] fields to remove from htl: {}",obsoleteFields);

        for(String removefiled:obsoleteFields){
            content = removeFieldBlocks(content, removefiled); // assign back
            log.info("removed........");
        }


        Map<String,String>existingFieldTypes=new LinkedHashMap<>();
        for(ComponentField field:oldRequest.getFields()){
            existingFieldTypes.put(field.getFieldName(),field.getFieldType());
        }
        log.info("[updateHTLFromRequest] existingFieldTypes : {}",existingFieldTypes);
        // --- Step 2: Determine fields to remove ---
        List<String> requestFieldNames = request.getFields().stream()
                .map(ComponentField::getFieldName)
                .collect(Collectors.toList());

        log.info("[updateHTLFromRequest] request fields : {}",requestFieldNames);
        Set<String> fieldsToRemove = new HashSet<>(existingFields);
       // fieldsToRemove.removeAll(requestFieldNames);
       log.info("[updateHTLFromRequest]  fields to remove : {}",requestFieldNames);

        // --- Step 2.1: Handle type changes ---
        //Map<String, String> fieldsWithTypeChanges = fieldsWithChangedTypes(request, existingFieldTypes);

        Set<String> fieldsWithTypeChanges = new HashSet<>();
        for (ComponentField field : request.getFields()) {
            String existingType = existingFieldTypes.get(field.getFieldName());
            if (existingType != null && !existingType.equalsIgnoreCase(field.getFieldType())) {
                fieldsWithTypeChanges.add(field.getFieldName());
                log.info("[updateHTLFromRequest] field type changed : {}",field.getFieldName()+"    field type  "+
                        field.getFieldType());
            }
        }

// Add fields with type changes to removal set
        fieldsToRemove.addAll(fieldsWithTypeChanges);
       log.info("[updateHTLFromRequest] fields added when type changed : {}",fieldsWithTypeChanges);


        // --- Step 4: Update or insert normal fields ---
        // --- Step 4: Update or insert normal fields ---
        existingFields = fieldsToRemove; // refresh after removal
        StringBuilder normalFieldsToInsert = new StringBuilder();
        log.info("[updateHTLFromRequest] existing fields : {}", existingFields);
        Set<ComponentField> multifields = new LinkedHashSet<>();

        for (ComponentField field : request.getFields()) {
            boolean typeChanged = fieldsWithTypeChanges.contains(field.getFieldName());

            if (typeChanged) {
                // Remove old block
                content = removeFieldBlocks(content, field.getFieldName());
                // Remove from existingFields so it gets re-added
                existingFields.remove(field.getFieldName());
                log.info("[updateHTLFromRequest] removed old block due to type change: {}", field.getFieldName());
            }

            if ("multifield".equalsIgnoreCase(field.getFieldType())) {
                multifields.add(field); // handle separately
            } else if (!existingFields.contains(field.getFieldName())) {
                // Rebuild the field with new type
                normalFieldsToInsert.append(buildNormalFieldHTL(field, existingFields));
                log.info("[updateHTLFromRequest] normal field added/updated at top level: {}", field);
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
            content = updateMultifieldInPlace(content, mf,existingFields);
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
        log.info("[extractTopLevelFields] pattern to extract fields : {}",pattern);
        log.info("[extractTopLevelFields] matcher to extract fields : {}",matcher);
        while (matcher.find()) {
            fields.add(matcher.group(1));
            log.info("[extractTopLevelFields] fields added to set : {}",matcher.group(1));
        }
        return fields;
    }
    private String removeFieldBlocks(String content, String fieldName) {
        // --- 1. Remove full <sly> blocks referencing the field ---
        String regexSlyBlock =
                "<sly[^>]*data-sly-test\\s*=\\s*\"\\$\\{model\\." + Pattern.quote(fieldName) + ".*?\\}\"[^>]*>.*?</sly>\\s*";
        content = Pattern.compile(regexSlyBlock, Pattern.DOTALL).matcher(content).replaceAll("");

        // --- 2. Remove <p>, <div>, <span>, etc. containing ${model.field} ---
        String regexTag =
                "<([a-zA-Z0-9]+)(\\s+[^>]*)?>[^<]*\\$\\{model\\." + Pattern.quote(fieldName) + ".*?\\}.*?</\\1>\\s*";
        content = Pattern.compile(regexTag, Pattern.DOTALL).matcher(content).replaceAll("");

        // --- 3. Remove stray self-closing tags (edge cases) ---
        String regexSelfClosing =
                "<[a-zA-Z0-9]+[^>]*\\$\\{model\\." + Pattern.quote(fieldName) + ".*?\\}[^>]*/>\\s*";
        content = Pattern.compile(regexSelfClosing, Pattern.DOTALL).matcher(content).replaceAll("");

        // --- 4. Clean up empty sly blocks (after child removal) ---
        content = content.replaceAll("<sly[^>]*>\\s*</sly>", "");

        return content;
    }

    private String buildFieldHTL(ComponentField field, String context,Set<String> existingFields) {

        String name = field.getFieldName();
        String label = field.getFieldLabel();
        if (existingFields.contains(name)) {
            log.info("[buildFieldHTL] Field '{}' already exists in HTL, skipping addition.", name);
            return ""; // skip adding
        }
        log.info("[buildFieldHTL] added field in the htl: {}",name);
        switch (field.getFieldType().toLowerCase()) {
            case "fileupload":
                log.info("[buildFieldHTL] add in the htl {}","<p>" + label + ": <img src=\"${" + context + "." + name +
                        "}\" alt=\"Image\" style=\"max-width:100%; height:auto;\"/></p>\n");
                return "<p>" + label + ": <img src=\"${" + context + "." + name +
                        "}\" alt=\"Image\" style=\"max-width:100%; height:auto;\"/></p>\n";

            case "pathfield":
                log.info("[buildFieldHTL] add in the htl {}","<sly data-sly-test=\"${" + context + "." + name +
                        "}\">\n" +
                        "  <p>" + label + ": <a href=\"${" + context + "." + name + "}\">" + label + "</a></p>\n" +
                        "</sly>\n");
                return "<sly data-sly-test=\"${" + context + "." + name + "}\">\n" +
                        "  <p>" + label + ": <a href=\"${" + context + "." + name + "}\">" + label + "</a></p>\n" +
                        "</sly>\n";

            case "checkbox":
            case "switch":
                log.info("[buildFieldHTL] add in the htl {}","<sly data-sly-test=\"${" + context + "." + name +
                        "}\">\n" +
                        "  <p>" + label + ": <input type=\"checkbox\" disabled checked=\"checked\"/></p>\n" +
                        "</sly>\n");
                return "<sly data-sly-test=\"${" + context + "." + name + "}\">\n" +
                        "  <p>" + label + ": <input type=\"checkbox\" disabled checked=\"checked\"/></p>\n" +
                        "</sly>\n";

            case "radiogroup":
            case "select":
                log.info("[buildFieldHTL] add in the htl {}","<sly data-sly-test=\"${" + context +
                        "." + name + "}\">\n" +
                        "  <p>" + label + ": ${" + context + "." + name + "}</p>\n" +
                        "</sly>\n");
                return "<sly data-sly-test=\"${" + context + "." + name + "}\">\n" +
                        "  <p>" + label + ": ${" + context + "." + name + "}</p>\n" +
                        "</sly>\n";

            case "multiselect":
            case "tagfield":
                log.info("[buildFieldHTL] add in the htl {}","<sly data-sly-test=\"${" + context + "." + name + " && " + context + "." + name + ".size > 0}\">\n" +
                        "  <ul data-sly-list.item=\"${" + context + "." + name + "}\">\n" +
                        "    <li>${item}</li>\n" +
                        "  </ul>\n" +
                        "</sly>\n");

                return "<sly data-sly-test=\"${" + context + "." + name + " && " + context + "." + name + ".size > 0}\">\n" +
                        "  <ul data-sly-list.item=\"${" + context + "." + name + "}\">\n" +
                        "    <li>${item}</li>\n" +
                        "  </ul>\n" +
                        "</sly>\n";

            default:
                log.info("[buildFieldHTL] add in the htl {}","<p>" + label + ": ${" + context + "." + name + " @ context=\"html\"}</p>\n");
                return "<p>" + label + ": ${" + context + "." + name + " @ context=\"html\"}</p>\n";
        }
    }

    // For top-level fields (normal ones)
    private String buildNormalFieldHTL(ComponentField field,Set<String> existingFields) {
        return buildFieldHTL(field,"model",existingFields );
    }

    // For nested fields (inside multifield)
    private String buildNestedFieldHTL(ComponentField nested,Set<String> existingFields) {
        log.info("[buildNestedFieldHTL] nested fileds to add : {}",nested);
        return buildFieldHTL(nested, "item",existingFields );
    }


    /**
     * Updates multifield in-place. Only adds missing nested fields, removes obsolete nested fields.
     */
    private String updateMultifieldInPlace(String content, ComponentField multifield, Set<String> existingFields) {
        String fieldName = multifield.getFieldName();

        // Pattern to find the multifield <ul> block (with or without sly)
        String regexUl =
                "(<sly[^>]*>\\s*)?<ul[^>]*data-sly-list\\.item=\"\\$\\{model\\."
                        + Pattern.quote(fieldName) + "\\}\"[^>]*>.*?</ul>";
        Pattern pattern = Pattern.compile(regexUl, Pattern.DOTALL);
        Matcher matcher = pattern.matcher(content);

        if (matcher.find()) {
            String oldBlock = matcher.group(0);
            // Wrap in sly if not present
            boolean hasSly = oldBlock.trim().startsWith("<sly");
            String workingBlock = hasSly ? oldBlock : "<sly>" + oldBlock+"</sly>";
            System.out.println("old block ........................"+oldBlock);
            // Merge nested fields
            String updatedBlock = mergeNestedFields(workingBlock, multifield, existingFields);
           log.info("[updateMultifieldInPlace] updatedBlock : {}",updatedBlock);
            // If original had no sly, unwrap
            if (!hasSly) {
                System.out.println("*************************************");
                updatedBlock = updatedBlock.replaceAll("^<sly>", "").replaceAll("</sly>$", "");
            }
            System.out.println("updated block &&&&&&&&&&&&&&&&&&&&&&&&& "+updatedBlock);
            content = matcher.replaceFirst(Matcher.quoteReplacement(updatedBlock));
            log.info("[updateMultifieldInPlace] Updated multifield block in-place: {}", fieldName);
        } else {
            // Multifield doesn't exist: insert new block
            String newBlock = buildFullMultifieldHTL(multifield, existingFields);
            int insertIndex = content.lastIndexOf("</sly>");
            if (insertIndex == -1) insertIndex = content.length();
            content = content.substring(0, insertIndex) + "\n" + newBlock + content.substring(insertIndex);
            log.info("[updateMultifieldInPlace] Inserted new multifield block: {}", fieldName);
        }

        return content;
    }


    /**
     * Merge nested fields: remove deleted fields, append new fields
     */
    private String mergeNestedFields(String block, ComponentField multifield, Set<String> existingFields) {
        log.info("[mergeNestedFields] Merging nested fields for '{}'", multifield.getFieldName());

        Pattern ulPattern = Pattern.compile("(<ul[^>]*>)(.*?)(</ul>)", Pattern.DOTALL);
        Matcher ulMatcher = ulPattern.matcher(block);
        if (!ulMatcher.find()) return block;

        String ulStart = ulMatcher.group(1);
        String ulContent = ulMatcher.group(2);
        String ulEnd = ulMatcher.group(3);

        // Map old blocks by field name
        Map<String, String> existingBlocks = new LinkedHashMap<>();
        Pattern childPattern = Pattern.compile("(<sly[^>]*>.*?</sly>|<p>.*?</p>)", Pattern.DOTALL);
        Matcher childMatcher = childPattern.matcher(ulContent);
        while (childMatcher.find()) {
            String raw = childMatcher.group(1);
            Matcher nameMatcher = Pattern.compile("\\$\\{item\\.([a-zA-Z0-9_]+)").matcher(raw);
            if (nameMatcher.find()) {
                existingBlocks.put(nameMatcher.group(1), raw);
            }
        }

        // Build only requested nested fields
        Set<String> requested = multifield.getNestedFields().stream()
                .map(ComponentField::getFieldName)
                .collect(Collectors.toSet());

        StringBuilder newUlContent = new StringBuilder();
        for (ComponentField nested : multifield.getNestedFields()) {
            String name = nested.getFieldName();
            if (existingBlocks.containsKey(name)) {
                newUlContent.append(existingBlocks.get(name)).append("\n");
            } else {
                newUlContent.append(buildNestedFieldHTL(nested, existingFields));
            }
        }

        // Return cleaned UL without obsolete nested blocks
        return ulStart + "\n" + newUlContent + ulEnd;
    }

//    private String mergeNestedFields(String block, ComponentField multifield, Set<String> existingFields) {
//        log.info("[mergeNestedFields] Merging nested fields for '{}'", multifield.getFieldName());
//
//        // Extract the <ul> content
//        Pattern ulPattern = Pattern.compile("(<ul[^>]*>)(.*?)(</ul>)", Pattern.DOTALL);
//        Matcher ulMatcher = ulPattern.matcher(block);
//        if (!ulMatcher.find()) return block;
//
//        String ulStart = ulMatcher.group(1);
//        String ulContent = ulMatcher.group(2);
//        String ulEnd = ulMatcher.group(3);
//
//        Map<String, String> existingBlocks = new LinkedHashMap<>();
//
//        // Split content by direct child blocks (<p> or <sly>)
//        Pattern childPattern = Pattern.compile("(<sly[^>]*>.*?</sly>|<p>.*?</p>)", Pattern.DOTALL);
//        Matcher childMatcher = childPattern.matcher(ulContent);
//        while (childMatcher.find()) {
//            String raw = childMatcher.group(1);
//            // detect field name
//            Matcher nameMatcher = Pattern.compile("\\$\\{item\\.([a-zA-Z0-9_]+)").matcher(raw);
//            if (nameMatcher.find()) {
//                String name = nameMatcher.group(1);
//                existingBlocks.put(name, raw);
//            }
//        }
//
//        StringBuilder newUlContent = new StringBuilder();
//
//        for (ComponentField nested : multifield.getNestedFields()) {
//            String name = nested.getFieldName();
//            if (existingBlocks.containsKey(name)) {
//                String existingBlock = existingBlocks.get(name);
//                if ("multifield".equalsIgnoreCase(nested.getFieldType())) {
//                    // recursive merge for nested multifield
//                    newUlContent.append(mergeNestedFields(existingBlock, nested, existingFields)).append("\n");
//                } else {
//                    newUlContent.append(existingBlock).append("\n");
//                }
//            } else {
//                // new nested field → add
//                newUlContent.append(buildNestedFieldHTL(nested, existingFields));
//            }
//        }
//
//        return ulStart + "\n" + newUlContent + ulEnd;
//    }



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
        log.info("[removeNestedField removed block: {}",block);
        return block;
    }


    private String buildFullMultifieldHTL(ComponentField multifield,Set<String> existingFields) {
        String fieldName = multifield.getFieldName();
        log.info("[buildFullMultifieldHTL] multifield name : {}",fieldName);
        StringBuilder sb = new StringBuilder();
        sb.append("<sly data-sly-test=\"${model.").append(fieldName).append(" && model.").append(fieldName).
                append(".size > 0}\">\n")
                .append("  <ul data-sly-list.item=\"${model.").append(fieldName).append("}\">\n");
        for (ComponentField nested : multifield.getNestedFields()) {
            log.info("[buildFullMultifieldHTL] nested multifield name : {}",nested);

            sb.append(buildNestedFieldHTL(nested,existingFields));
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