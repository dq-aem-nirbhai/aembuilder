package com.aem.builder.service.impl;

import com.aem.builder.model.AemProjectModel;
import com.aem.builder.model.ProjectDetails;
import com.aem.builder.service.AemProjectService;
import com.aem.builder.service.ComponentService;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.File;
import java.io.IOException;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
@AllArgsConstructor
@Slf4j
public class AemProjectServiceImpl implements AemProjectService {

    private final ComponentService componentService;

    private static final String PROJECTS_DIR = "generated-projects";

    @Override
    public void generateAemProject(AemProjectModel aemProjectModel) {
        try {
            // Create directory structure
            String baseDir = System.getProperty("user.dir") + "/generated-projects/";
            File directory = new File(baseDir);
            if (!directory.exists()) {
                directory.mkdirs();
            }

            // Prepare project-specific details
            String appId = aemProjectModel.getProjectName().toLowerCase().replace(" ", "-");

            // Prepare Maven command for clean 6.5.13 structure
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
                            "-DincludeDispatcherAMS=n " + // You can change to "n" if you don't want AMS either
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

            // OS-specific ProcessBuilder (cross-platform)
            ProcessBuilder processBuilder;
            if (System.getProperty("os.name").toLowerCase().contains("win")) {
                processBuilder = new ProcessBuilder("cmd.exe", "/c", command);
            } else {
                processBuilder = new ProcessBuilder("bash", "-c", command);
            }

            // Set working directory
            processBuilder.directory(directory);

            // Stream logs to console for debugging
            processBuilder.redirectErrorStream(true);
            Process process = processBuilder.start();
            process.getInputStream().transferTo(System.out);

            int exitCode = process.waitFor();

            // Output Results
            if (exitCode == 0) {
                System.out.println("AEM project generated successfully.");
            } else {
                System.out.println(" AEM project generation failed with exit code: " + exitCode);
            }

            // Step 1: Copy selected components to ui.apps
            String componentsTargetPath = baseDir + appId + "/ui.apps/src/main/content/jcr_root/apps/" + appId
                    + "/components/";
            File contentFolder = new File(componentsTargetPath);
            if (!contentFolder.exists()) {
                contentFolder.mkdirs();
            }
            componentService.copySelectedComponents(aemProjectModel.getSelectedComponents(), componentsTargetPath, appId);

        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
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
        Path tempDir = Files.createTempDirectory("aem-import");
        try (java.util.zip.ZipInputStream zis = new java.util.zip.ZipInputStream(file.getInputStream())) {
            java.util.zip.ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                Path out = tempDir.resolve(entry.getName());
                if (entry.isDirectory()) {
                    Files.createDirectories(out);
                } else {
                    Files.createDirectories(out.getParent());
                    Files.copy(zis, out);
                }
            }
        }

        // Determine project root - allow zips with or without a top-level folder
        Path rootDir = tempDir;
        if (!Files.exists(rootDir.resolve("pom.xml"))) {
            File[] dirs = tempDir.toFile().listFiles(File::isDirectory);
            if (dirs != null && dirs.length == 1 && new File(dirs[0], "pom.xml").exists()) {
                rootDir = dirs[0].toPath();
            } else {
                org.apache.commons.io.FileUtils.deleteDirectory(tempDir.toFile());
                throw new IOException("Uploaded file is not a valid AEM project structure");
            }
        }

        // Validate ui.apps structure exists
        if (!Files.exists(rootDir.resolve("ui.apps/src/main/content/jcr_root"))) {
            org.apache.commons.io.FileUtils.deleteDirectory(tempDir.toFile());
            throw new IOException("Uploaded file is not a valid AEM project structure");
        }

        String projectName = rootDir.getFileName().toString();
        Path target = Paths.get(PROJECTS_DIR, projectName);
        if (Files.exists(target)) {
            org.apache.commons.io.FileUtils.deleteDirectory(tempDir.toFile());
            throw new IOException("Project already exists: " + projectName);
        }
        Files.move(rootDir, target);
        org.apache.commons.io.FileUtils.deleteDirectory(tempDir.toFile());
    }
}