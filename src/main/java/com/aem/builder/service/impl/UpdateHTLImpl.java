package com.aem.builder.service.impl;

import com.aem.builder.model.DTO.ComponentField;
import com.aem.builder.service.UpdateHTL;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
public class UpdateHTLImpl implements UpdateHTL {

    /**
     * Update HTL content by replacing the hasContent block
     */

    public void updateHTLFile(Path htlFile, List<ComponentField> fields) throws IOException {
        String htlContent = Files.readString(htlFile);
        String updatedContent = updateHTLFromDialog(htlContent, fields);
        Files.writeString(htlFile, updatedContent);
    }

    @Override
    public String updateHTLFromDialog(String htlContent, List<ComponentField> fields) {
        log.info("Original HTL: {}", htlContent);

        StringBuilder newInner = new StringBuilder();
        for (ComponentField field : fields) {
            log.info("fields from request,{}", field);
            newInner.append(generateHTLSnippet(field)).append("\n");
        }

        String regex = "(<sly[^>]*data-sly-test\\.hasContent[^>]*>)([\\s\\S]*?)(</sly>)";
        Pattern pattern = Pattern.compile(regex, Pattern.DOTALL);
        Matcher matcher = pattern.matcher(htlContent);

        if (matcher.find()) {
            log.info("✅ Found hasContent block, updating...");
            StringBuffer sb = new StringBuffer();
            matcher.appendReplacement(sb,
                    Matcher.quoteReplacement(matcher.group(1) + "\n" + newInner + matcher.group(3)));
            matcher.appendTail(sb);
            String updated = sb.toString();
            log.info("Updated HTL: {}", updated);
            return updated;
        } else {
            log.warn("⚠️ hasContent block not found in HTL, regenerating file...");
            return """
            <sly data-sly-use.model="com.aem.chair.core.models.DemocomponentModel"/>
            <sly data-sly-use.placeholderTemplate="core/wcm/components/commons/v1/templates.html"/>
            <sly data-sly-test.hasContent="${!model.empty}">
            %s
            </sly>
            <sly data-sly-call="${placeholderTemplate.placeholder @ isEmpty = !hasContent}" />
            """.formatted(newInner);
        }
    }




    /**
     * Generate HTL snippet for a field based on its type
     */
    private String generateHTLSnippet(ComponentField field) {
        String name = field.getFieldName();
        String label = field.getFieldLabel();

        switch (field.getFieldType().toLowerCase()) {
            case "textfield":
            case "numberfield":
            case "password":
            case "hidden":
                return "  <p>" + label + ": ${model." + name + "}</p>";

            case "textarea":
            case "richtext":
                return "  <div>${model." + name + " @ context='html'}</div>";

            case "checkbox":
            case "switch":
                return """
                  <sly data-sly-test="${model.%s}">
                    <p>%s: ${model.%s}</p>
                  </sly>
                  """.formatted(name, label, name);

            case "select":
            case "multiselect":
                return """
                  <ul data-sly-list.option="${model.%s}">
                    <li>${option}</li>
                  </ul>
                  """.formatted(name);

            case "datepicker":
            case "tagfield":
            case "pathfield":
            case "colorfield":
                return "  <p>" + label + ": ${model." + name + "}</p>";

            case "fileupload":
                return "  <img src='${model." + name + "}' alt='" + label + "' />";

            case "multifield":
                StringBuilder mf = new StringBuilder();
                mf.append("  <ul data-sly-list.item=\"${model.").append(name).append("}\">\n");
                if (field.getNestedFields() != null) {
                    for (ComponentField nested : field.getNestedFields()) {
                        mf.append("    ").append(generateHTLSnippetForNested(nested, "item")).append("\n");
                    }
                }
                mf.append("  </ul>");
                return mf.toString();

            default:
                return "  <p>" + label + ": ${model." + name + "}</p>";
        }
    }

    /**
     * Generate snippet for nested multifield children
     */
    private String generateHTLSnippetForNested(ComponentField field, String parentVar) {
        String name = field.getFieldName();
        String label = field.getFieldLabel();

        switch (field.getFieldType().toLowerCase()) {
            case "textarea":
            case "richtext":
                return "  <div>${" + parentVar + "." + name + " @ context='html'}</div>";
            default:
                return "  <p>" + label + ": ${" + parentVar + "." + name + "}</p>";
        }
    }

    /**
     * Regenerate a full HTL file from scratch (used on initial creation)
     */
    private void updateHTL(Path htlFile, List<ComponentField> fields, String modelClass) throws IOException {
        StringBuilder sb = new StringBuilder();

        sb.append("<sly data-sly-use.model=\"").append(modelClass).append("\"/>\n");
        sb.append("<sly data-sly-use.placeholderTemplate=\"core/wcm/components/commons/v1/templates.html\"/>\n");
        sb.append("<sly data-sly-test.hasContent=\"${!model.empty}\">\n");

        for (ComponentField field : fields) {
            sb.append(generateHTLSnippet(field)).append("\n");
        }

        sb.append("</sly>\n");
        sb.append("<sly data-sly-call=\"${placeholderTemplate.placeholder @ isEmpty = !hasContent}\" />\n");

        Files.createDirectories(htlFile.getParent());
        Files.writeString(htlFile, sb.toString());
    }
}
