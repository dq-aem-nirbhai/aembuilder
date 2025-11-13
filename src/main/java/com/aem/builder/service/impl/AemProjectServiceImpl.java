package com.aem.builder.service.impl;

import com.aem.builder.config.MavenCommandConfig;
import com.aem.builder.constants.AemProjectConstants;
import com.aem.builder.model.AemProjectModel;
import com.aem.builder.model.ProjectDetails;
import com.aem.builder.service.AemProjectService;
import com.aem.builder.service.ComponentService;
import com.aem.builder.util.*;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.api.Git;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static com.aem.builder.constants.AemProjectConstants.*;


@Service
@AllArgsConstructor
@Slf4j
public class AemProjectServiceImpl implements AemProjectService {

    private final ComponentService componentService;
    private MavenCommandConfig mavenCommandConfig;


    /**
     * Generates a new AEM project using Maven archetype.
     * Steps include creating base directory, building Maven command, executing it,
     * and performing post-generation tasks such as updating POM and copying components.
     *
     * @param projectModel model containing project details
     * @throws IOException if project already exists or generation fails
     */
    @Override
    public void generateProject(AemProjectModel projectModel) throws IOException {
        log.info("[generateProject] Starting AEM project generation for '{}'", projectModel.getProjectName());

        Path baseDir = createBaseDirectory();
        String appId = formatAppId(projectModel.getProjectName());
        Path projectPath = baseDir.resolve(appId);

        if (Files.exists(projectPath)) {
            throw new IOException("[generateProject] Project already exists: " + projectModel.getProjectName());
        }

        String mavenCommand = buildMavenCommand(projectModel, appId);
        executeMavenCommand(mavenCommand, baseDir.toFile());
        handlePostGenerationTasks(projectPath, baseDir, projectModel, appId);

        log.info("[generateProject] AEM project '{}' generated successfully at {}", projectModel.getProjectName(), projectPath);
    }

    /**
     * Creates the base directory for generated projects if it does not exist.
     * Ensures all subsequent project generation is performed in a consistent location.
     *
     * @return Path to the base directory
     * @throws IOException if directory cannot be created
     */
    private Path createBaseDirectory() throws IOException {
        Path path = Paths.get(System.getProperty("user.dir"), PROJECTS_DIR);
        if (!Files.exists(path)) {
            Files.createDirectories(path);
            log.info("[createBaseDirectory] Created base directory: {}", path);
        } else {
            log.info("[createBaseDirectory] Base directory already exists: {}", path);
        }
        return path;
    }

    /**
     * Converts a project name to a valid appId by converting to lowercase and replacing spaces with hyphens.
     * This ensures the generated project folder name and Maven artifactId are valid.
     *
     * @param projectName project name
     * @return formatted appId
     */
    private String formatAppId(String projectName) {
        String appId = projectName.toLowerCase().replaceAll("\\s+", "-");
        log.info("[formatAppId] Formatted appId '{}' from project name '{}'", appId, projectName);
        return appId;
    }

    /**
     * Builds the Maven command string by replacing placeholders in the JSON template with project-specific values.
     * This command is later executed to generate the project structure automatically.
     *
     * @param model project details
     * @param appId formatted appId
     * @return fully populated Maven command
     */
    private String buildMavenCommand(AemProjectModel model, String appId) {
        String command = mavenCommandConfig.getCommandTemplate()
                .replace("{PLUGIN_GROUP}", MAVEN_PLUGIN_GROUP)
                .replace("{PLUGIN_ARTIFACT}", MAVEN_PLUGIN_ARTIFACT)
                .replace("{PLUGIN_VERSION}", MAVEN_PLUGIN_VERSION)
                .replace("{ARCHETYPE_GROUP}", ARCHETYPE_GROUP)
                .replace("{ARCHETYPE_ARTIFACT}", ARCHETYPE_ARTIFACT)
                .replace("{ARCHETYPE_VERSION}", ARCHETYPE_VERSION)
                .replace("{APP_TITLE}", model.getProjectName())
                .replace("{APP_ID}", appId)
                .replace("{GROUP_ID}", model.getPackageName())
                .replace("{AEM_VERSION}", model.getVersion());
        log.info("[buildMavenCommand] Built Maven command for project '{}'", model.getProjectName());
        return command;
    }

    /**
     * Executes the given Maven command in the specified working directory.
     * Redirects the process output to the console and throws IOException if the command fails.
     *
     * @param command    Maven command to execute
     * @param workingDir directory to execute the command in
     * @throws IOException if the process fails or is interrupted
     */
    private void executeMavenCommand(String command, File workingDir) throws IOException {
        log.info("[executeMavenCommand] Executing Maven command in directory '{}': {}", workingDir, command);

        ProcessBuilder builder = System.getProperty("os.name").toLowerCase().contains("win") ?
                new ProcessBuilder("cmd.exe", "/c", command) :
                new ProcessBuilder("bash", "-c", command);

        builder.directory(workingDir);
        builder.redirectErrorStream(true);

        try {
            Process process = builder.start();
            try (InputStream inputStream = process.getInputStream()) {
                inputStream.transferTo(System.out);
            }

            int exitCode = process.waitFor();
            if (exitCode != 0) {
                throw new IOException("[executeMavenCommand] Maven build failed with exit code: " + exitCode);
            }
            log.info("[executeMavenCommand] Maven command executed successfully.");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("[executeMavenCommand] Project generation was interrupted", e);
        }
    }

    /**
     * Handles post-generation tasks for a newly created project.
     * Tasks include updating POM properties, modifying configuration filter mode,
     * and copying selected components to the project.
     *
     * @param projectPath  path to the newly generated project
     * @param baseDir      base projects directory
     * @param projectModel project details
     * @param appId        formatted appId
     * @throws IOException if any post-generation operation fails
     */
    private void handlePostGenerationTasks(Path projectPath, Path baseDir, AemProjectModel projectModel, String appId) throws IOException {
        log.info("[handlePostGenerationTasks] Starting post-generation tasks for project '{}'", projectModel.getProjectName());

        Path pomFile = projectPath.resolve(AemProjectConstants.POM_XML);
        if (Files.exists(pomFile)) {
            PomXmlUtil.updatePomProperty(pomFile, "createdDate", List.of("importDate", "cloneDate"));
            log.info("[handlePostGenerationTasks] Updated POM properties for project '{}'", projectModel.getProjectName());
        }

        updateConfFilterMode(baseDir.toString(), appId);
        log.info("[handlePostGenerationTasks] Updated configuration filter mode for project '{}'", projectModel.getProjectName());

        Path componentsPath = baseDir.resolve(Paths.get(appId, AemProjectConstants.COMPONENTS_PATH, appId, "components"));
        if (!Files.exists(componentsPath)) {
            Files.createDirectories(componentsPath);
            log.info("[handlePostGenerationTasks] Created components directory: {}", componentsPath);
        }

        componentService.copySelectedComponents(projectModel.getSelectedComponents(), componentsPath.toString(), appId);
        log.info("[handlePostGenerationTasks] Copied selected components for project '{}'", projectModel.getProjectName());
    }

    /**
     * Retrieves details of all existing AEM projects in the projects directory.
     * Parses each project's pom.xml to extract version, groupId, and creation/import dates.
     *
     * @return list of ProjectDetails representing all existing projects
     */
    @Override
    public List<ProjectDetails> getAllProjects() {
        log.info("[getAllProjects] Retrieving all projects from '{}'", PROJECTS_DIR);
        List<ProjectDetails> projects = new ArrayList<>();
        File projectsFolder = new File(PROJECTS_DIR);

        if (!projectsFolder.exists() || !projectsFolder.isDirectory()) {
            log.info("[getAllProjects] Projects folder '{}' does not exist or is not a directory", PROJECTS_DIR);
            return projects;
        }

        String[] projectNames = projectsFolder.list((dir, name) -> new File(dir, name).isDirectory());
        if (projectNames == null || projectNames.length == 0) {
            log.info("[getAllProjects] No projects found in '{}'", PROJECTS_DIR);
            return projects;
        }

        for (String projectName : projectNames) {
            Path projectPath = projectsFolder.toPath().resolve(projectName);
            File pomFile = projectPath.resolve("pom.xml").toFile();

            String version = "Unknown";
            String groupId = "Unknown";
            String displayName = projectName;
            String createdDate = "Unknown";
            String importDate = "Unknown";
            String cloneDate = "Unknown";

            if (pomFile.exists()) {
                try (InputStream is = Files.newInputStream(pomFile.toPath())) {
                    DocumentBuilder builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
                    Document doc = builder.parse(is);
                    doc.getDocumentElement().normalize();

                    if (doc.getElementsByTagName("groupId").getLength() > 0) {
                        groupId = doc.getElementsByTagName("groupId").item(0).getTextContent();
                    }

                    NodeList nameNodes = doc.getElementsByTagName("name");
                    if (nameNodes.getLength() > 0) {
                        displayName = nameNodes.item(0).getTextContent();
                    }

                    NodeList dependencies = doc.getElementsByTagName("dependency");
                    for (int i = 0; i < dependencies.getLength(); i++) {
                        Element dependency = (Element) dependencies.item(i);
                        String depGroup = getTagValue(dependency, "groupId");
                        String depArtifact = getTagValue(dependency, "artifactId");
                        if ("com.adobe.aem".equals(depGroup) && "uber-jar".equals(depArtifact)) {
                            version = getTagValue(dependency, "version");
                            break;
                        }
                    }

                    Map.Entry<String, String> tagEntry = getFirstAvailableTag(doc, "createdDate", "importDate", "cloneDate");

                    String tagName = tagEntry.getKey();
                    String tagValue = tagEntry.getValue();

                    switch (tagName) {
                        case "createdDate" -> createdDate = tagValue;
                        case "importDate"  -> importDate = tagValue;
                        case "cloneDate"   -> cloneDate = tagValue;
                        default            -> log.warn("[DATE] No matching tag found.");
                    }

                } catch (Exception e) {
                    log.info("[getAllProjects] Failed to parse pom.xml for project '{}': {}", projectName, e.getMessage());
                }
            } else {
                log.info("[getAllProjects] pom.xml not found for project '{}'", projectName);
            }
            String dateLabel;
            String effectiveDate;

            if (createdDate != null && !createdDate.equals("Unknown")) {
                dateLabel = "Created";
                effectiveDate = createdDate;
            } else if (cloneDate != null && !cloneDate.equals("Unknown")) {
                dateLabel = "Cloned";
                effectiveDate = cloneDate;
            } else {
                dateLabel = "Imported";
                effectiveDate = importDate;
            }


            projects.add(new ProjectDetails(
                    displayName,
                    projectName,
                    version,
                    groupId,
                    createdDate,
                    importDate,
                    cloneDate,
                    projectPath.toString(),
                    dateLabel,
                    effectiveDate
            ));
        }

        log.info("[getAllProjects] Total projects found: {}", projects.size());
        return projects;
    }

    /** Utility: get text content of a tag inside an element */
    private String getTagValue(Element element, String tagName) {
        return (element.getElementsByTagName(tagName).getLength() > 0) ? element.getElementsByTagName(tagName).item(0).getTextContent() : "Unknown";
    }

    /** Utility: return first available tag value among multiple tag names */
    private Map.Entry<String, String> getFirstAvailableTag(Document doc, String... tagNames) {
        for (String tag : tagNames) {
            NodeList nodes = doc.getElementsByTagName(tag);
            if (nodes.getLength() > 0) {
                String value = nodes.item(0).getTextContent();
                return Map.entry(tag, value); // return both name and value
            }
        }
        return Map.entry("Unknown", "Unknown");
    }


    /**
     * Generates a ZIP of the given project for download.
     * Walks through all project files and adds them to the ZIP output stream.
     *
     * @param projectName project to zip
     * @return byte array of the ZIP content
     * @throws IOException if project is missing or ZIP creation fails
     */
    @Override
    public byte[] downloadProjectZip(String projectName) throws IOException {
        log.info("[downloadProjectZip] Preparing ZIP download for project '{}'", projectName);

        Path sourceDir = Paths.get(PROJECTS_DIR, projectName);
        if (!Files.exists(sourceDir) || !Files.isDirectory(sourceDir)) {
            throw new IOException("[downloadProjectZip] Project not found: " + projectName);
        }

        // Call ZipUtil instead of inline logic
        byte[] zipBytes = ZipUtil.zipDirectory(sourceDir);

        log.info("[downloadProjectZip] ZIP creation completed for project '{}'", projectName);
        return zipBytes;
    }

    /**
     * Imports a project ZIP uploaded by the user.
     * Validates structure, checks for duplicates, moves project to target directory,
     * and updates POM and config filter.
     *
     * @param file uploaded ZIP
     * @throws IOException if validation or import fails
     */
    @Override
    public void importProject(MultipartFile file) throws IOException {
        log.info("[importProject] Starting import for uploaded project ZIP: {}", file.getOriginalFilename());

        // Step 0: Save uploaded file to temp ZIP
        Path tempZip = Files.createTempFile("aem-upload", ".zip");
        try (InputStream uploadedStream = file.getInputStream()) {
            Files.copy(uploadedStream, tempZip, StandardCopyOption.REPLACE_EXISTING);
        }

        try {
            // Step 1: Extract artifactId using ZipUtil + PomXmlUtil
            String artifactId = ZipUtil.getArtifactIdFromZip(tempZip);
            if (artifactId == null || artifactId.isBlank()) {
                throw new IOException("[importProject] Invalid project: Missing or empty <artifactId> in pom.xml.");
            }
            log.info("[importProject] Found artifactId '{}' in ZIP", artifactId);

            // Step 2: Fail fast if project already exists
            Path projectsDir = Paths.get(PROJECTS_DIR);
            Files.createDirectories(projectsDir);
            Path target = projectsDir.resolve(artifactId);
            if (Files.exists(target)) {
                throw new IOException("[importProject] Import failed: Project with artifactId '" + artifactId + "' already exists.");
            }

            // Step 3: Extract full ZIP to temporary directory
            Path tempDir = Files.createTempDirectory("aem-import");
            ZipUtil.extractZip(tempZip, tempDir);

            // Step 4: Locate root directory of AEM project (pom.xml + ui.apps module)
            Path rootDir;
            try (Stream<Path> stream = Files.walk(tempDir)) {
                rootDir = stream
                        .filter(p -> p.getFileName().toString().equals("pom.xml"))
                        .map(Path::getParent)
                        .filter(p -> p != null && Files.exists(p.resolve("ui.apps/src/main/content/jcr_root")))
                        .findFirst()
                        .orElse(null);
            }

            if (rootDir == null) {
                throw new IOException("[importProject] Invalid AEM project: Missing pom.xml or ui.apps module.");
            }

            // Step 5: Move validated project to final location
            Files.move(rootDir, target);
            log.info("[importProject] Project '{}' imported successfully to '{}'", artifactId, target);

            // Step 6: Update POM properties and config
            Path pomFile = target.resolve("pom.xml");
            PomXmlUtil.updatePomProperty(pomFile, "importDate", List.of("createdDate", "cloneDate"));
            updateConfFilterMode(PROJECTS_DIR, artifactId);

            // Step 7: Cleanup temp directory
            FileUtils.deleteDirectory(tempDir.toFile());
            log.info("[importProject] Cleaned up temporary extraction directory '{}'", tempDir);

        } finally {
            // Ensure temp ZIP is deleted even on failure
            Files.deleteIfExists(tempZip);
        }
    }


    /**
     * Deletes an existing project from the file system.
     *
     * @param projectName name of the project to delete
     * @throws IOException if project does not exist or deletion fails
     */
    @Override
    public void deleteProject(String projectName) throws IOException {
        Path projectPath = Paths.get(PROJECTS_DIR, projectName);
        log.info("[DELETE] Requested project deletion: {}", projectName);

        if (!Files.exists(projectPath)) {
            log.warn("[DELETE] Project '{}' not found at {}", projectName, projectPath);
            throw new IOException("Project not found: " + projectName);
        }

        try {
            org.apache.commons.io.FileUtils.deleteDirectory(projectPath.toFile());
            log.info("[DELETE] Successfully deleted project '{}' at {}", projectName, projectPath);
        } catch (IOException e) {
            log.error("[DELETE] Failed to delete project '{}' at {}. Error: {}",
                    projectName, projectPath, e.getMessage(), e);
            throw e;
        }
    }



    /**
     * Checks whether a project exists in the projects directory.
     *
     * @param projectName project to check
     * @return true if project exists, false otherwise
     */
    @Override
    public boolean projectExists(String projectName) {
        if (projectName == null || projectName.isBlank()) {
            log.info("[projectExists] Project name is null or blank");
            return false;
        }

        Path projectPath = Paths.get(System.getProperty("user.dir"), PROJECTS_DIR, projectName);
        boolean exists = Files.exists(projectPath) && Files.isDirectory(projectPath);
        log.info("[projectExists] Check for project '{}': {}", projectName, exists);
        return exists;
    }

    /**
     * Extracts the Maven artifactId from an uploaded project ZIP.
     *
     * @param file uploaded ZIP
     * @return artifactId as string
     * @throws IOException if ZIP or pom.xml is invalid
     */
    @Override
    public String extractArtifactId(MultipartFile file) throws IOException {
        log.info("[extractArtifactId] Extracting artifactId from uploaded project ZIP: {}", file.getOriginalFilename());

        Path tempZip = Files.createTempFile("aem-upload", ".zip");
        file.transferTo(tempZip.toFile());

        String artifactId = null;

        try (ZipFile zipFile = new ZipFile(tempZip.toFile())) {
            Enumeration<? extends ZipEntry> entries = zipFile.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (!entry.isDirectory() && entry.getName().endsWith("pom.xml")) {
                    try (InputStream input = zipFile.getInputStream(entry)) {
                        artifactId = PomXmlUtil.parseArtifactId(input);
                        if (artifactId != null && !artifactId.isBlank()) {
                            log.info("[extractArtifactId] Found artifactId '{}'", artifactId);
                            break;
                        }
                    } catch (Exception e) {
                        log.error("[extractArtifactId] Failed to parse pom.xml: {}", e.getMessage(), e);
                        throw new IOException("[extractArtifactId] Error reading pom.xml from ZIP", e);
                    }
                }
            }
        } finally {
            Files.deleteIfExists(tempZip);
            log.info("[extractArtifactId] Temporary ZIP file deleted: {}", tempZip);
        }

        if (artifactId == null || artifactId.isBlank()) {
            throw new IllegalArgumentException("[extractArtifactId] Could not find artifactId in pom.xml");
        }

        return artifactId;
    }

    /**
     * Clones an AEM project repository from Git.
     * Validates the project, sets up local branches, and moves it to the projects directory.
     *
     * @param repoUrl Git repository URL
     * @throws IOException if clone or validation fails
     */
    @Override
    public void cloneProject(String repoUrl) throws IOException {
        log.info("[cloneProject] Cloning repository: {}", repoUrl);

        Path tempDir = null;
        try {
            // Step 1: Create temporary directory for clone
            tempDir = Files.createTempDirectory("aem-clone-");
            log.info("[cloneProject] Temporary clone directory created at {}", tempDir);

            // Step 2: Clone repository using GitUtil
            Git git = GitUtil.cloneRepository(repoUrl, tempDir);

            // Step 3: Setup local branches using GitUtil
            GitUtil.setupLocalBranches(git);

            // Step 4: Validate AEM project structure
            if (!AemValidationUtil.hasAemStructure(tempDir)) {
                throw new IOException("[cloneProject] Repository does not contain a valid AEM project.");
            }

            // Step 5: Extract artifactId from pom.xml
            Path rootPom = tempDir.resolve("pom.xml");
            String artifactId = PomXmlUtil.readArtifactId(rootPom);
            if (artifactId == null || artifactId.isBlank()) {
                throw new IOException("[cloneProject] pom.xml does not contain a valid <artifactId>.");
            }

            // Step 6: Prepare final projects directory
            Path projectsDir = FileUtil.createDirectories(PROJECTS_DIR);
            Path target = projectsDir.resolve(artifactId);
            if (Files.exists(target)) {
                throw new IOException("[cloneProject] Clone failed: project '" + artifactId + "' already exists.");
            }

            // Step 7: Move or copy project to final location
            FileUtil.moveOrCopyProject(tempDir, target);

            // Step 8: Update pom.xml property
            Path pomFile = target.resolve("pom.xml");
            if (Files.exists(pomFile)) {
                PomXmlUtil.updatePomProperty(pomFile, "cloneDate", List.of("importDate", "createdDate"));
                updateConfFilterMode(PROJECTS_DIR,artifactId);
            }

            log.info("[cloneProject] Repository '{}' cloned successfully as project '{}'", repoUrl, artifactId);

        } catch (Exception e) {
            FileUtil.cleanupTemp(tempDir);
            log.error("[cloneProject] Failed to clone repository '{}': {}", repoUrl, e.getMessage(), e);
            if (e instanceof IOException) throw (IOException) e;
            throw new IOException("[cloneProject] Failed to clone repository: " + repoUrl, e);
        }
    }

    /**
     * Updates the filter.xml mode for /conf/{appId} from "merge" → "replace".
     */
    private void updateConfFilterMode(String baseDir, String projectName) throws IOException {
        Path filterPath = Paths.get(baseDir, projectName, "ui.content/src/main/content/META-INF/vault/filter.xml");


        if (Files.notExists(filterPath)) {
            log.warn("[updateConfFilterMode] filter.xml not found at {}", filterPath);
            return;
        }
        String appId= AemUtil.getAppId(PROJECTS_DIR,projectName);
        List<String> lines = Files.readAllLines(filterPath);
        List<String> updatedLines = new ArrayList<>();
        boolean updated = false;

        String targetFilter = "<filter root=\"/conf/" + appId + "\"";

        for (String line : lines) {
            if (line.contains(targetFilter)) {
                if (line.contains("mode=\"merge\"")) {
                    line = line.replace("mode=\"merge\"", "mode=\"replace\"");
                    updated = true;
                    log.info("[updateConfFilterMode] Updated /conf/{} filter mode from merge → replace", appId);
                } else if (line.contains("mode=\"replace\"")) {
                    log.info("[updateConfFilterMode] Filter for /conf/{} already set to mode=replace. Skipping.", appId);
                }
            }
            updatedLines.add(line);
        }

        if (updated) {
            Files.write(filterPath, updatedLines);
            log.info("[updateConfFilterMode] filter.xml updated successfully for /conf/{}", appId);
        }
    }

}