package com.aem.builder.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
public class ToolUtil {
    public static void ensureContentXml(Path toolPagePath, String project, String toolName) throws IOException {
        Path contentXml = toolPagePath.resolve(".content.xml");

        if (Files.exists(contentXml)) {
            log.info("ℹ️ .content.xml already exists for tool '{}', skipping generation.", toolName);
            updateExistingContentXml(contentXml,project,toolName);
            return;
        }

        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<jcr:root xmlns:sling=\"http://sling.apache.org/jcr/sling/1.0\"\n" +
                "          xmlns:jcr=\"http://www.jcp.org/jcr/1.0\"\n" +
                "          jcr:primaryType=\"cq:Page\">\n" +
                "  <jcr:content\n" +
                "      jcr:primaryType=\"cq:PageContent\"\n" +
                "      sling:resourceType=\"" + project + "/components/authoring/tools/" + toolName + "\"\n" +
                "      jcr:title=\"" + StringUtils.capitalize(toolName.replace('-', ' ')) + "\"/>\n" +
                "</jcr:root>";

        Files.createDirectories(toolPagePath);
        Files.writeString(contentXml, xml);
        log.info("✅ Created .content.xml for tool '{}' at {}", toolName, contentXml);
    }
    public static void updateExistingContentXml(Path contentXml, String project, String toolName) throws IOException {
        String content = Files.readString(contentXml);
        if (!content.contains("sling:resourceType=\"" + project + "/components/" + toolName + "\"")) {
            content = content.replaceAll(
                    "sling:resourceType=\"[^\"]+\"",
                    "sling:resourceType=\"" + project + "/components/authoring/tools/" + toolName + "\""
            );
            //        sling:resourceType="charger/components/authoring/tools/generic-csv-importer"

            Files.writeString(contentXml, content, StandardOpenOption.TRUNCATE_EXISTING);
            log.info("🔄 Updated sling:resourceType in existing .content.xml for '{}'", toolName);
        } else {
            log.info("✅ sling:resourceType already correct for '{}'", toolName);
        }
    }
    public static void updateToolNavHref(Path cqToolNavDest, String projectName, String toolName) {
        try {
            // Locate .content.xml file in the copied tool folder
            Path contentXml = cqToolNavDest.resolve(".content.xml");
            if (!Files.exists(contentXml)) {
                log.warn("⚠️ No .content.xml found at {}", contentXml);
                return;
            }

            String xml = Files.readAllLines(contentXml).stream().collect(Collectors.joining("\n"));

            // Replace href value
            String updatedXml = xml.replaceAll(
                    "href=\"/apps/[^/]+/content/[^\"]+\"",
                    "href=\"/apps/" + projectName + "/content/" + toolName + ".html\""
            );

            // Only update if something actually changed
            if (!xml.equals(updatedXml)) {
                Files.write(contentXml, updatedXml.getBytes(), StandardOpenOption.TRUNCATE_EXISTING);
                log.info("✅ Updated href in {}", contentXml);
            } else {
                log.info("ℹ️ No href replacement needed in {}", contentXml);
            }

        } catch (IOException e) {
            log.error("❌ Error updating href for {}: {}", cqToolNavDest, e.getMessage(), e);
        }
    }
    public static void addMavenDependency(Path pomPath, String groupId, String artifactId, String version) {
        try {
            String pomContent = Files.readString(pomPath);

            if (!pomContent.contains(artifactId)) {
                String dependency = String.format(
                        "    <dependency>\n" +
                                "        <groupId>%s</groupId>\n" +
                                "        <artifactId>%s</artifactId>\n" +
                                "        <version>%s</version>\n" +
                                "    </dependency>\n",
                        groupId, artifactId, version
                );

                // Insert before closing </dependencies>
                pomContent = pomContent.replace("</dependencies>", dependency + "</dependencies>");
                Files.writeString(pomPath, pomContent);
                log.info("✅ Added dependency {}:{} to {}", groupId, artifactId, pomPath);
            } else {
                log.info("ℹ️ Dependency {} already present in {}", artifactId, pomPath);
            }

        } catch (IOException e) {
            log.error("❌ Failed to update pom.xml for dependency {}: {}", artifactId, e.getMessage());
        }
    }
    public static void addFilterRoots(Path filterXmlPath, List<String> roots) {
        try {
            // Ensure parent directory exists
            Files.createDirectories(filterXmlPath.getParent());

            StringBuilder updatedContent = new StringBuilder();

            if (Files.exists(filterXmlPath)) {
                updatedContent.append(Files.readString(filterXmlPath));
            } else {
                // Create basic structure if missing
                updatedContent.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
                updatedContent.append("<workspaceFilter version=\"1.0\">\n");
                updatedContent.append("</workspaceFilter>");
            }

            String content = updatedContent.toString();

            for (String root : roots) {
                if (!content.contains("root=\"" + root + "\"")) {
                    String filterEntry = String.format("    <filter root=\"%s\"/>\n", root);
                    content = content.replace("</workspaceFilter>", filterEntry + "</workspaceFilter>");
                    log.info("✅ Added filter root: {}", root);
                } else {
                    log.info("ℹ️ Filter root already exists: {}", root);
                }
            }

            Files.writeString(filterXmlPath, content);
            log.info("✅ Updated filter.xml at {}", filterXmlPath);

        } catch (IOException e) {
            log.error("❌ Failed to update filter.xml: {}", e.getMessage());
        }
    }
public static String getModelPath(String projectName,String modelType){
        String modelPath="/conf/"+projectName+"/settings/dam/cfm/models/" + modelType;
        return modelPath;
}

}
