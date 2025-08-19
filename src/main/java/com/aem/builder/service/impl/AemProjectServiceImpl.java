package com.aem.builder.service.impl;

import com.aem.builder.model.AemProjectModel;
import com.aem.builder.model.ProjectDetails;
import com.aem.builder.service.AemProjectService;
import com.aem.builder.service.ComponentService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.File;
import java.io.IOException;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

@Service
@AllArgsConstructor
@Slf4j
public class AemProjectServiceImpl implements AemProjectService {

    private final ComponentService componentService;

    private static final String PROJECTS_DIR = "generated-projects";

    @Override
    public void generateAemProject(AemProjectModel aemProjectModel) throws IOException {
        String baseDir = System.getProperty("user.dir") + "/generated-projects/";
        File directory = new File(baseDir);
        if (!directory.exists()) {
            directory.mkdirs();
        }

        String appId = aemProjectModel.getProjectName().toLowerCase().replace(" ", "-");
        Path projectPath = Paths.get(baseDir, appId);
        if (Files.exists(projectPath)) {
            throw new IOException("Project already exists: " + aemProjectModel.getProjectName());
        }

        String command = String.format(
                "mvn -B org.apache.maven.plugins:maven-archetype-plugin:3.2.1:generate " +
                        "-DarchetypeGroupId=com.adobe.aem " +
                        "-DarchetypeArtifactId=aem-project-archetype " +
                        "-DarchetypeVersion=41 " +
                        "-DappTitle=\"%s\" " +
                        "-DappId=\"%s\" " +
                        "-DgroupId=\"%s\" " +
                        "-DaemVersion=\"%s\" " +
                        "-Darchetype.interactive=false " +
                        "-DincludeDispatcherConfig=y " +
                        "-DincludeDispatcherCloud=n " +
                        "-DincludeDispatcherAMS=n " +
                        "-DincludeFrontendModuleGeneral=n " +
                        "-DincludeFrontendModuleReact=n " +
                        "-DincludeFrontendModuleAngular=n " +
                        "-DincludeFrontendModuleReactFormsAF=n " +
                        "-DincludeCommerce=n " +
                        "-DincludeCommerceFrontend=n " +
                        "-Dlanguage=en " +
                        "-Dcountry=us " +
                        "-DsingleCountry=n",
                aemProjectModel.getProjectName(),
                appId,
                aemProjectModel.getPackageName(),
                aemProjectModel.getVersion());

        ProcessBuilder processBuilder;
        if (System.getProperty("os.name").toLowerCase().contains("win")) {
            processBuilder = new ProcessBuilder("cmd.exe", "/c", command);
        } else {
            processBuilder = new ProcessBuilder("bash", "-c", command);
        }

        processBuilder.directory(directory);
        processBuilder.redirectErrorStream(true);
        try {
            Process process = processBuilder.start();
            process.getInputStream().transferTo(System.out);
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                throw new IOException("AEM project generation failed with exit code: " + exitCode);
            }
            String componentsTargetPath = baseDir + appId + "/ui.apps/src/main/content/jcr_root/apps/" + appId + "/components/";
            File contentFolder = new File(componentsTargetPath);
            if (!contentFolder.exists()) {
                contentFolder.mkdirs();
            }
            componentService.copySelectedComponents(aemProjectModel.getSelectedComponents(), componentsTargetPath, appId);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Project generation interrupted", e);
        }
    }



    @Override
    public List<ProjectDetails> getAllProjects() {
        File projectsFolder = new File(PROJECTS_DIR);
        String[] projectNames = projectsFolder.list((dir, name) -> new File(dir, name).isDirectory());

        List<ProjectDetails> projects = new ArrayList<>();
        if (projectNames != null) {
            for (String name : projectNames) {
                File pomFile = new File(projectsFolder, name + "/pom.xml");
                String version = "Unknown";
                String groupId = "Unknown";
                String createdDate = "Unknown";
                String path = new File(projectsFolder, name).getPath();

                try {
                    if (pomFile.exists()) {
                        var builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
                        Document doc = builder.parse(pomFile);
                        NodeList dependencies = doc.getElementsByTagName("dependency");

                        for (int i = 0; i < dependencies.getLength(); i++) {
                            Element dependency = (Element) dependencies.item(i);
                            String group = dependency.getElementsByTagName("groupId").item(0).getTextContent();
                            String artifact = dependency.getElementsByTagName("artifactId").item(0).getTextContent();
                            if ("com.adobe.aem".equals(group) && "uber-jar".equals(artifact)) {
                                version = dependency.getElementsByTagName("version").item(0).getTextContent();
                                break;
                            }
                        }

                        groupId = doc.getElementsByTagName("groupId").item(0).getTextContent();
                    }
                } catch (Exception ignored) {
                }

                try {
                    BasicFileAttributes attr = Files.readAttributes(new File(projectsFolder, name).toPath(),
                            BasicFileAttributes.class);
                    createdDate = attr.creationTime().toInstant().atZone(java.time.ZoneId.systemDefault())
                            .format(DateTimeFormatter.ofPattern("MMM dd, yyyy HH:mm"));
                } catch (Exception ignored) {
                }

                projects.add(new ProjectDetails(name, version, groupId, createdDate, path));
            }
        }
        return projects;
    }

    @Override
    public byte[] getProjectZip(String projectName) throws IOException {
        String projectPath = PROJECTS_DIR + File.separator + projectName;
        Path sourceDir = Paths.get(projectPath);
        if (!Files.exists(sourceDir)) {
            throw new IOException("Project not found: " + projectName);
        }
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             ZipOutputStream zos = new ZipOutputStream(baos)) {
            Files.walk(sourceDir)
                    .filter(path -> !Files.isDirectory(path))
                    .forEach(path -> {
                        ZipEntry zipEntry = new ZipEntry(sourceDir.relativize(path).toString().replace("\\", "/"));
                        try {
                            zos.putNextEntry(zipEntry);
                            Files.copy(path, zos);
                            zos.closeEntry();
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    });
            zos.finish();
            return baos.toByteArray();
        }
    }

    @Override
    public void importProject(org.springframework.web.multipart.MultipartFile file) throws IOException {
        // Save temp zip
        Path tempZip = Files.createTempFile("aem-upload", ".zip");
        Files.copy(file.getInputStream(), tempZip, java.nio.file.StandardCopyOption.REPLACE_EXISTING);

        String artifactId = null;

        //  Step 1: Quickly extract only pom.xml from zip
        try (java.util.zip.ZipFile zipFile = new java.util.zip.ZipFile(tempZip.toFile())) {
            java.util.Enumeration<? extends java.util.zip.ZipEntry> entries = zipFile.entries();
            while (entries.hasMoreElements()) {
                java.util.zip.ZipEntry entry = entries.nextElement();
                if (entry.getName().endsWith("pom.xml")) {
                    try (InputStream in = zipFile.getInputStream(entry)) {
                        DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
                        DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
                        org.w3c.dom.Document doc = dBuilder.parse(in);
                        doc.getDocumentElement().normalize();
                        artifactId = doc.getElementsByTagName("artifactId").item(0).getTextContent();
                    } catch (Exception e) {
                        throw new IOException("Failed to read pom.xml. Ensure the ZIP contains a valid Maven project.", e);
                    }
                    break;
                }
            }
        }

        if (artifactId == null || artifactId.isBlank()) {
            Files.deleteIfExists(tempZip);
            throw new IOException("Invalid project: Missing or empty <artifactId> in pom.xml.");
        }

        //  Step 2: Fail fast if project already exists
        Path projectsDir = Paths.get(PROJECTS_DIR);
        Files.createDirectories(projectsDir);
        Path target = projectsDir.resolve(artifactId);

        if (Files.exists(target)) {
            Files.deleteIfExists(tempZip);
            throw new IOException("Import failed: A project with artifactId '" + artifactId + "' already exists.");
        }

        //  Step 3: If safe, then do full extraction
        Path tempDir = Files.createTempDirectory("aem-import");
        try (java.util.zip.ZipFile zipFile = new java.util.zip.ZipFile(tempZip.toFile())) {
            java.util.Enumeration<? extends java.util.zip.ZipEntry> entries = zipFile.entries();
            while (entries.hasMoreElements()) {
                java.util.zip.ZipEntry entry = entries.nextElement();
                Path out = tempDir.resolve(entry.getName());
                if (entry.isDirectory()) {
                    Files.createDirectories(out);
                } else {
                    Files.createDirectories(out.getParent());
                    try (InputStream is = zipFile.getInputStream(entry)) {
                        Files.copy(is, out, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    }
                }
            }
        } finally {
            Files.deleteIfExists(tempZip);
        }

        //  Step 4: Validate structure (ui.apps, pom.xml, etc.)
        Path rootDir;
        try (var stream = Files.walk(tempDir)) {
            rootDir = stream
                    .filter(p -> p.getFileName().toString().equals("pom.xml"))
                    .map(Path::getParent)
                    .filter(p -> p != null && Files.exists(p.resolve("ui.apps/src/main/content/jcr_root")))
                    .findFirst()
                    .orElse(null);
        }

        if (rootDir == null) {
            org.apache.commons.io.FileUtils.deleteDirectory(tempDir.toFile());
            throw new IOException("Invalid AEM project: Missing pom.xml or ui.apps module.");
        }

        try {
            Files.move(rootDir, target);
        } catch (IOException e) {
            org.apache.commons.io.FileUtils.deleteDirectory(tempDir.toFile());
            throw new IOException("Failed to import project '" + artifactId + "'. Could not move files.", e);
        }

        // Cleanup temp extraction dir
        if (Files.exists(tempDir)) {
            org.apache.commons.io.FileUtils.deleteDirectory(tempDir.toFile());
        }
    }


    @Override
    public void deleteProject(String projectName) throws IOException {
        Path projectPath = Paths.get(PROJECTS_DIR, projectName);
        if (!Files.exists(projectPath)) {
            throw new IOException("Project not found: " + projectName);
        }
        org.apache.commons.io.FileUtils.deleteDirectory(projectPath.toFile());
    }


    @Override
    public boolean projectExists(String projectName) {
        if (projectName == null || projectName.isBlank()) {
            return false;
        }
        Path projectPath = Paths.get(System.getProperty("user.dir"), PROJECTS_DIR, projectName);
        File folder = projectPath.toFile();
        return folder.exists() && folder.isDirectory();
    }

    @Override
    public String extractArtifactId(MultipartFile file) throws IOException {
        // Save uploaded zip to a temp file
        File tempZip = File.createTempFile("aem-upload", ".zip");
        file.transferTo(tempZip);

        String artifactId = null;

        try (ZipFile zipFile = new ZipFile(tempZip)) {
            Enumeration<? extends ZipEntry> entries = zipFile.entries();

            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();

                // look for pom.xml
                if (entry.getName().endsWith("pom.xml") && !entry.isDirectory()) {
                    try (InputStream input = zipFile.getInputStream(entry)) {
                        artifactId = parseArtifactIdFromPom(input);
                        if (artifactId != null) {
                            break;
                        }
                    }
                }
            }
        } finally {
            Files.deleteIfExists(tempZip.toPath());
        }

        if (artifactId == null || artifactId.isBlank()) {
            throw new IllegalArgumentException("Could not find artifactId in pom.xml");
        }

        return artifactId;
    }



    private String parseArtifactIdFromPom(InputStream pomStream) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(pomStream);
            doc.getDocumentElement().normalize();

            return doc.getElementsByTagName("artifactId").item(0).getTextContent();
        } catch (Exception e) {
            log.error("Failed to parse pom.xml for artifactId: {}", e.getMessage(), e);
            return null;
        }
    }


}