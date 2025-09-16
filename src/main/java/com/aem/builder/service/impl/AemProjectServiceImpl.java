package com.aem.builder.service.impl;

import com.aem.builder.model.AemProjectModel;
import com.aem.builder.model.ProjectDetails;
import com.aem.builder.service.AemProjectService;
import com.aem.builder.service.ComponentService;
import lombok.AllArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.api.CreateBranchCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.ListBranchCommand;
import org.eclipse.jgit.lib.Ref;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.ZonedDateTime;
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
            Path pomFile = projectPath.resolve("pom.xml");
            if (Files.exists(pomFile)) {
                updatePomProperty(pomFile, "createdDate", List.of("importDate", "cloneDate"));
            }
            updateConfFilterMode(baseDir,appId);
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
                String importDate = "Unknown";
                String cloneDate = "Unknown";
                String displayName="Unknown";
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
                        NodeList nameNodes = doc.getElementsByTagName("name");
                        if (nameNodes.getLength() > 0) {
                            displayName = nameNodes.item(0).getTextContent();
                        } else {
                            displayName = name;
                        }

                        NodeList createdDateNodes = doc.getElementsByTagName("createdDate");
                        NodeList importDateNodes = doc.getElementsByTagName("importDate");
                        NodeList cloneDateNodes = doc.getElementsByTagName("cloneDate");

                        if (createdDateNodes.getLength() > 0) {
                            createdDate = createdDateNodes.item(0).getTextContent();
                        } else if (importDateNodes.getLength() > 0) {
                            importDate = importDateNodes.item(0).getTextContent();
                        } else if (cloneDateNodes.getLength() > 0) {
                            cloneDate = cloneDateNodes.item(0).getTextContent();
                        }

                    }

                } catch (Exception ignored) {
                }

                projects.add(new ProjectDetails(displayName,name, version, groupId, createdDate, importDate, cloneDate,
                        path));
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

        Path pomFile = target.resolve("pom.xml");

        updatePomProperty(pomFile, "importDate", List.of("createdDate", "cloneDate"));

        updateConfFilterMode(PROJECTS_DIR, artifactId);

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

    @SneakyThrows
    @Override
    public void cloneProject(String repoUrl) {
        // 1) Clone into a brand new empty temp dir
        Path tempDir = Files.createTempDirectory("aem-clone-");

        try (Git git = Git.cloneRepository()
                .setURI(repoUrl)
                .setDirectory(tempDir.toFile())
                .setCloneAllBranches(true)
                .setBranch("refs/heads/main")
                .call()) {

            // Create local branches for all remotes
            List<Ref> remoteBranches = git.branchList()
                    .setListMode(ListBranchCommand.ListMode.REMOTE)
                    .call();

            for (Ref remoteRef : remoteBranches) {
                String fullName = remoteRef.getName(); // refs/remotes/origin/feature-x
                if (fullName.startsWith("refs/remotes/origin/")) {
                    String branchName = fullName.replace("refs/remotes/origin/", "");

                    // Skip HEAD reference
                    if ("HEAD".equals(branchName)) continue;

                    // Check if already exists locally
                    boolean exists = git.branchList().call().stream()
                            .anyMatch(ref -> ref.getName().equals("refs/heads/" + branchName));

                    if (!exists) {
                        git.branchCreate()
                                .setName(branchName)
                                .setStartPoint(fullName)
                                .setUpstreamMode(CreateBranchCommand.SetupUpstreamMode.TRACK)
                                .call();
                    }
                }
            }

            // 1) Validate if it’s an AEM project
            if (!isAemProject(tempDir)) {
                throw new IOException("The given repository has no valid AEM project.");
            }

            // 2) Read artifactId from root pom.xml
            Path rootPom = tempDir.resolve("pom.xml");
            String artifactId = readArtifactId(rootPom);
            if (artifactId == null || artifactId.isBlank()) {
                throw new IOException("pom.xml does not contain a valid <artifactId>.");
            }

            // 3) Guard against duplicates
            Path projectsDir = Paths.get(PROJECTS_DIR);
            Files.createDirectories(projectsDir);
            Path target = projectsDir.resolve(artifactId);
            if (Files.exists(target)) {
                throw new IOException("Clone failed: project '" + artifactId + "' already exists.");
            }

            // 4) Move or copy directory
            try {
                Files.move(tempDir, target, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException crossFs) {
                FileUtils.copyDirectory(tempDir.toFile(), target.toFile());
                FileUtils.deleteDirectory(tempDir.toFile());
            }
            Path pomFile = target.resolve("pom.xml");
            if (Files.exists(pomFile)) {
                updatePomProperty(pomFile, "cloneDate", List.of("importDate", "createdDate"));
            }
        } catch (Exception e) {
            cleanupTemp(tempDir);
            if (e instanceof IOException) throw (IOException) e;
            throw new IOException("Failed to clone repository: " + e.getMessage(), e);
        }
    }

    public boolean isAemProject(Path repoRoot) {
        Path rootPom = repoRoot.resolve("pom.xml");
        if (Files.notExists(rootPom)) {
            return false;
        }

        try {
            boolean foundPom = Files.walk(repoRoot, 6)
                    .filter(p -> p.getFileName().toString().equalsIgnoreCase("pom.xml"))
                    .filter(this::isNotJunk)
                    .anyMatch(this::isAemModulePom);

            return foundPom || hasAemStructure(repoRoot);

        } catch (IOException e) {
            return false;
        }
    }

    private boolean isAemModulePom(Path pomPath) {
        try {
            String xml = Files.readString(pomPath);

            // Packaging types unique to AEM
            if (xml.contains("<packaging>bundle</packaging>")
                    || xml.contains("<packaging>content-package</packaging>")
                    || xml.contains("<packaging>all</packaging>")) {
                return true;
            }

            // Maven plugins used by AEM
            if (xml.contains("filevault-package-maven-plugin")
                    || xml.contains("content-package-maven-plugin")) {
                return true;
            }

            // Typical dependencies
            if (xml.contains("com.day.jcr.vault")
                    || xml.contains("com.adobe.cq")) {
                return true;
            }

        } catch (IOException ignore) {}
        return false;
    }

    private boolean hasAemStructure(Path repoRoot) {
        return Files.isDirectory(repoRoot.resolve("core"))
                && Files.isDirectory(repoRoot.resolve("ui.apps"))
                && Files.isDirectory(repoRoot.resolve("ui.content"));
    }

    private boolean isNotJunk(Path path) {
        String p = path.toString().toLowerCase();
        return !(p.contains(".git") || p.contains("target") || p.contains("node_modules"));
    }

    private void cleanupTemp(Path tempDir) {
        try {
            if (Files.exists(tempDir)) {
                FileUtils.deleteDirectory(tempDir.toFile());
            }
        } catch (IOException ignore) {}
    }

    private String readArtifactId(Path pomFile) throws IOException {
        try {
            DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
            dbf.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            // Avoid XXE
            dbf.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            dbf.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");

            DocumentBuilder dBuilder = dbf.newDocumentBuilder();
            Document doc = dBuilder.parse(pomFile.toFile());
            doc.getDocumentElement().normalize();
            NodeList nodes = doc.getElementsByTagName("artifactId");
            return nodes.getLength() > 0 ? nodes.item(0).getTextContent() : null;
        } catch (Exception e) {
            throw new IOException("Failed to read artifactId from pom.xml.", e);
        }
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

    private void updateConfFilterMode(String baseDir, String appId) throws IOException {
        Path filterPath = Paths.get(baseDir, appId, "ui.content/src/main/content/META-INF/vault/filter.xml");

        if (!Files.exists(filterPath)) {
            log.warn("filter.xml not found at {}", filterPath);
            return;
        }

        List<String> lines = Files.readAllLines(filterPath);
        List<String> updatedLines = new ArrayList<>();

        boolean updated = false;

        for (String line : lines) {
            String targetFilter = "<filter root=\"/conf/" + appId + "\"";
            if (line.contains(targetFilter)) {
                if (line.contains("mode=\"merge\"")) {
                    // Replace merge → replace only if merge is found
                    line = line.replace("mode=\"merge\"", "mode=\"replace\"");
                    updated = true;
                    log.info("Updated /conf/{} filter mode from merge → replace", appId);
                } else if (line.contains("mode=\"replace\"")) {
                    // Already correct → no change
                    log.info("Filter for /conf/{} already set to mode=replace. Skipping.", appId);
                }
            }
            updatedLines.add(line);
        }

        if (updated) {
            Files.write(filterPath, updatedLines);
        }
    }

    private void updatePomProperty(Path pomFile, String propertyName, List<String> toRemove) throws IOException {
        try {
            DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
            org.w3c.dom.Document doc = dBuilder.parse(pomFile.toFile());
            doc.getDocumentElement().normalize();

            // Get or create <properties>
            NodeList propsList = doc.getElementsByTagName("properties");
            org.w3c.dom.Element propertiesElement;
            if (propsList.getLength() > 0) {
                propertiesElement = (org.w3c.dom.Element) propsList.item(0);
            } else {
                propertiesElement = doc.createElement("properties");
                doc.getDocumentElement().appendChild(propertiesElement);
            }

            // Remove only requested properties from <properties>
            if (toRemove != null && !toRemove.isEmpty()) {
                for (int i = propertiesElement.getChildNodes().getLength() - 1; i >= 0; i--) {
                    Node child = propertiesElement.getChildNodes().item(i);
                    if (child.getNodeType() == Node.ELEMENT_NODE
                            && toRemove.contains(child.getNodeName())) {
                        propertiesElement.removeChild(child);
                    }
                }
            }

            // Find existing property inside <properties>
            org.w3c.dom.Element existing = null;
            NodeList children = propertiesElement.getChildNodes();
            for (int i = 0; i < children.getLength(); i++) {
                Node n = children.item(i);
                if (n.getNodeType() == Node.ELEMENT_NODE && propertyName.equals(n.getNodeName())) {
                    existing = (org.w3c.dom.Element) n;
                    break;
                }
            }

            // Current timestamp
            String now = ZonedDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

            if (existing != null) {
                existing.setTextContent(now); // update
            } else {
                org.w3c.dom.Element newEl = doc.createElement(propertyName);
                newEl.setTextContent(now);
                propertiesElement.appendChild(newEl);
            }

            // Save pom.xml back
            Transformer transformer = TransformerFactory.newInstance().newTransformer();
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            transformer.transform(new DOMSource(doc), new StreamResult(pomFile.toFile()));

        } catch (Exception e) {
            throw new IOException("Failed to update pom.xml with property: " + propertyName, e);
        }
    }
}