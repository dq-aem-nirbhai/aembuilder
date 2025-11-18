package com.aem.builder.service.impl;

import com.aem.builder.service.ToolService;
import com.aem.builder.util.AemUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import com.aem.builder.util.ToolUtil;

import static com.aem.builder.constants.ToolConstants.*;

@Slf4j
@Service
public class ToolServiceImpl implements ToolService {



    @Override
    public void addToolsToExistingProject(String projectName, List<String> selectedTools) {
        log.info("[ToolServiceImpl] Adding {} tool(s) to existing project '{}'", selectedTools.size(), projectName);
        Path targetBase = Paths.get(PROJECTS_DIR, projectName, APPS_PATH
                );

        String baseDir = System.getProperty(USER_DIR_SYS_PROP);

        try {
            String appId = AemUtil.getAppId(PROJECTS_DIR, projectName);

            Path projectCorePomPath = Paths.get(baseDir, PROJECTS_DIR, projectName, "core", "pom.xml");
            ToolUtil.addMavenDependency(projectCorePomPath, "org.apache.poi", "poi-ooxml", "5.2.3");

            // 🔹 Base app paths
            Path projectRoot = Paths.get(baseDir, PROJECTS_DIR, projectName);
            Path appRoot = projectRoot.resolve(APPS_PATH);


            Path cqOverlayTarget = appRoot.resolve("cq");
            Path componentsTarget = appRoot.resolve(appId).resolve("components");

            Files.createDirectories(componentsTarget);

            // Step 🔹 Copy tool-component base files
            Path toolComponentSource = Paths.get(baseDir, TOOL_COMPONENT);
            Path toolComponentTarget = appRoot.resolve(appId).resolve("components");

            if (Files.exists(toolComponentSource)) {
                copyDirectory(toolComponentSource, toolComponentTarget);
                log.info("✅ Tool component base copied to: {}", toolComponentTarget);
            } else {
                log.warn("⚠️ tool-component folder not found at {}", toolComponentSource);
            }
            // Step 1️⃣: Overlay CQ (if not already)
            if (Files.notExists(cqOverlayTarget)) {
                copyDirectory(Paths.get(baseDir, OVERLAY_CQ_BASE), cqOverlayTarget);
                log.info("✅ Full CQ overlay copied to: {}", cqOverlayTarget);
            } else {

                log.info("ℹ️ CQ overlay already exists. Copying only tool-specific nav entries...");
                for (String toolName : selectedTools) {
                    Path cqToolNavSource = Paths.get(baseDir, OVERLAY_CQ_BASE,
                            TOOL_NAV, toolName);
                    Path cqToolNavDest = cqOverlayTarget.resolve(TOOL_NAV).resolve(toolName);

                    if (Files.exists(cqToolNavSource)) {
                        copyDirectory(cqToolNavSource, cqToolNavDest);
                        log.info("✅ Overlay nav for tool '{}' copied to: {}", toolName, cqToolNavDest);
                    } else {
                        log.warn("⚠️ Overlay nav for tool '{}' not found at {}", toolName, cqToolNavSource);
                    }
                }
            }

            // Step 2️⃣: Process each tool
            for (String toolName : selectedTools) {

                // (a) Copy AEM components
                Path toolSource = Paths.get(baseDir, TOOLS_BASE, toolName);
                // Place tool components under: components/authoring/tools/<toolName>
                Path toolDest = componentsTarget.resolve(Paths.get("authoring", "tools", toolName));
                Files.createDirectories(toolDest.getParent());

                copyIfExists(toolSource, toolDest, "Component");


                // (b) Copy CQ nav entry
                Path cqToolNavSource = Paths.get(baseDir, OVERLAY_CQ_BASE, TOOL_NAV, toolName,toolName);
                Path cqToolNavDest = cqOverlayTarget.resolve(TOOL_NAV).resolve(toolName).resolve(toolName);
                copyIfExists(cqToolNavSource, cqToolNavDest, "Nav Entry");

                // Update nav href
                ToolUtil.updateToolNavHref(cqToolNavDest, projectName, toolName);

                // Step 1️⃣.b️⃣: Copy geeksdemo base (if exists)
                Path geeksdemoSource = Paths.get(baseDir, GEEKSDEMO_RESOURCE_PATH);
                Path geeksdemoTarget = appRoot.resolve("geeksdemo"); // apps/<projectName> destination

                if (Files.exists(geeksdemoSource)) {
                    copyDirectory(geeksdemoSource, geeksdemoTarget);
                    log.info("✅ geeksdemo base copied to: {}", geeksdemoTarget);
                } else {
                    // Folder missing — create empty structure for future tool placement
                    try {
                        Path projectAppFolder = geeksdemoTarget.resolve(projectName);
                        Files.createDirectories(projectAppFolder);
                        log.info("📁 geeksdemo not found, created empty project app folder at: {}", projectAppFolder);
                    } catch (IOException e) {
                        log.error("❌ Error creating empty project app folder: {}", e.getMessage(), e);
                    }
                }

                // (c) Copy Tool Page content
                Path toolPageSource = Paths.get(baseDir, TOOL_PAGE_BASE, toolName);
                Path toolPageTarget = targetBase.resolve(projectName).resolve("content").resolve(toolName);

                ToolUtil.ensureContentXml(toolPageTarget, projectName, toolName);


                // Step 3️⃣: Copy tool Java classes (service, servlets, util)
                try {
                    Path toolServiceSource = Paths.get(baseDir, JAVA_SERVICE_PATH);
                    Path toolServletSource = Paths.get(baseDir, JAVA_SERVLET_PATH);
                    Path toolUtilSource = Paths.get(baseDir, JAVA_UTIL_PATH);

                    // ✅ Corrected Java base package path
                    Path targetJavaBase = Paths.get(baseDir, PROJECTS_DIR, projectName,
                            JAVA_FILES_PATH, projectName, "core");

                    Path serviceTarget = targetJavaBase.resolve("service");
                    Path servletTarget = targetJavaBase.resolve("servlets");
                    Path utilTarget = targetJavaBase.resolve("util");

                    if (Files.exists(toolServiceSource)) {
                        copyAndRepackageJavaFiles(toolServiceSource, serviceTarget, "com.aem.builder", "com.aem." + projectName + ".core",projectName);
                        log.info("✅ Tool services copied to {}", serviceTarget);
                    } else {
                        log.warn("⚠️ No tool service folder found at {}", toolServiceSource);
                    }

                    if (Files.exists(toolServletSource)) {
                        copyAndRepackageJavaFiles(toolServletSource, servletTarget, "com.aem.builder", "com.aem." + projectName + ".core",projectName);
                        log.info("✅ Tool servlets copied to {}", servletTarget);
                    } else {
                        log.warn("⚠️ No tool servlet folder found at {}", toolServletSource);
                    }

                    if (Files.exists(toolUtilSource)) {
                        copyAndRepackageJavaFiles(toolUtilSource, utilTarget, "com.aem.builder", "com.aem." + projectName + ".core",projectName);
                        log.info("✅ Tool utility classes copied to {}", utilTarget);
                    } else {
                        log.warn("⚠️ No tool util folder found at {}", toolUtilSource);
                    }

                } catch (Exception e) {
                    log.error("❌ Error copying tool Java classes: {}", e.getMessage(), e);
                }

            }

        } catch (Exception e) {
            log.error("[ToolServiceImpl] Error while adding tools: {}", e.getMessage(), e);
        }
        Path filterXmlPath = Paths.get(baseDir, PROJECTS_DIR, projectName,
                FILTER_FILE_PATH);

        List<String> rootsToAdd = Arrays.asList(
                "/apps/cq",
                "/apps/geeksdemo",
                "/apps/" + projectName + "/content"
        );

        ToolUtil.addFilterRoots(filterXmlPath, rootsToAdd);

    }

    /**
     * Helper: Copy directory if it exists
     */
    private void copyIfExists(Path source, Path dest, String label) throws IOException {
        if (!Files.exists(source)) {
            log.warn("⚠️ {} not found: {}", label, source);
            return;
        }
        copyDirectory(source, dest);
        log.info("✅ {} copied to {}", label, dest);
    }

    /**
     * Recursively copy directory structure
     */
    private void copyDirectory(Path source, Path target) throws IOException {
        try (Stream<Path> stream = Files.walk(source)) {
            stream.forEach(src -> {
                try {
                    Path dest = target.resolve(source.relativize(src));
                    if (Files.isDirectory(src)) {
                        Files.createDirectories(dest);
                    } else {
                        Files.copy(src, dest, StandardCopyOption.REPLACE_EXISTING);
                    }
                } catch (IOException e) {
                    log.error("❌ Error copying file {} → {}", src, target, e);
                }
            });
        }
    }

    private void copyAndRepackageJavaFiles(Path sourceDir, Path targetDir, String oldPackage, String newPackage, String projectName) throws IOException {
        Files.walk(sourceDir).forEach(src -> {
            try {
                Path dest = targetDir.resolve(sourceDir.relativize(src));

                if (Files.isDirectory(src)) {
                    Files.createDirectories(dest);
                } else if (src.toString().endsWith(".java")) {
                    String content = new String(Files.readAllBytes(src));

                    // 🔹 Replace package and imports
                    content = content.replace("package " + oldPackage, "package " + newPackage);
                    content = content.replace(oldPackage + ".", newPackage + ".");

                    // ✅ Remove `.tool.` segment from final package
                    content = content.replace(newPackage + ".tool.", newPackage + ".");

                    // 🔹 Adjust AEM-specific paths
                    content = content
                            .replaceAll("/conf/charger", "/conf/" + projectName)
                            .replaceAll("/content/charger", "/content/" + projectName)
                            .replaceAll("/apps/charger", "/apps/" + projectName)
                            .replaceAll("\"charger\"", "\"" + projectName + "\"");

                    Files.write(dest, content.getBytes(), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
                } else {
                    Files.copy(src, dest, StandardCopyOption.REPLACE_EXISTING);
                }
            } catch (IOException e) {
                log.error("❌ Error copying/repackaging file {} → {}", src, targetDir, e);
            }
        });
    }
    @Override
    public List<String> getExistingTools(String projectName){
        String toolsPath=PROJECTS_DIR+"/"+projectName+"/"+TOOL_PATH;
        File toolsDir = new File(toolsPath);
        List<String> folderNames = new ArrayList<>();

        if (toolsDir.exists() && toolsDir.isDirectory()) {
            File[] subDirs = toolsDir.listFiles(File::isDirectory);
            if (subDirs != null) {
                Arrays.stream(subDirs)
                        .forEach(folder -> folderNames.add(folder.getName()));
            }
        }
        return folderNames;
    }
    public List<String>fetchTools( String projectName){
        String toolsBasePath = System.getProperty("user.dir")
                + TOOL_RESOURCE_PATH;

        File baseDir = new File(toolsBasePath);
        if (!baseDir.exists() || !baseDir.isDirectory()) {
            log.warn("[ToolServiceImpl] Tools directory not found at {}", toolsBasePath);
            return List.of();
        }

        // List only folder names
        File[] dirs = baseDir.listFiles(File::isDirectory);
        List<String> toolNames = dirs != null
                ? Arrays.stream(dirs).map(File::getName).toList()
                : List.of();
        log.info("[ToolServiceImpl] tools found -> {}",toolNames);
        log.info("[ToolServiceImpl] Found {} tool(s)", toolNames.size());
        return toolNames;
    }
}