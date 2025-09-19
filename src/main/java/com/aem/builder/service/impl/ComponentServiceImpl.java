package com.aem.builder.service.impl;

import com.aem.builder.model.DTO.ComponentField;
import com.aem.builder.model.DTO.ComponentRequest;
import com.aem.builder.model.DTO.OptionItem;
import com.aem.builder.model.Enum.FieldType;
import com.aem.builder.service.ComponentService;
import com.aem.builder.util.AemUtil;
import com.aem.builder.util.FileGenerationUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import lombok.RequiredArgsConstructor;
import org.apache.commons.io.FileUtils;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.w3c.dom.*;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static com.aem.builder.constants.AemProjectConstants.USER_DIR_SYS_PROP;
import static com.aem.builder.constants.AemProjectConstants.UTF_8;
import static com.aem.builder.constants.ComponentConstants.*;
import static com.aem.builder.constants.ModelAttributeKeys.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class ComponentServiceImpl implements ComponentService {

    /**
     * Extracts the value of a given property from the provided content using a predefined regex pattern.
     * <p>
     * This method searches the input {@code content} for the specified {@code property}
     * and returns the first captured value if found.
     * If the content is null or the property is not found, an empty string is returned.
     *
     * @param content  The source string (e.g., XML/JSON text) where the property is searched.
     * @param property The property name whose value needs to be extracted.
     * @return The extracted value if the property exists; otherwise, an empty string.
     */
    private String extractProperty(String content, String property) {
        log.info("[extractProperty] Entering method with property: {}", property);

        if (content == null) {
            log.info("[extractProperty] Content is null, returning empty string");
            return "";
        }

        Pattern p = Pattern.compile(property + PROPERTY_REGEX);
        log.info("[extractProperty] Regex pattern created for property '{}': {}", property, p.pattern());

        Matcher m = p.matcher(content);
        log.info("[extractProperty] Starting search for property '{}' in content", property);

        String value = m.find() ? m.group(1) : "";
        log.info("[extractProperty] Property '{}' found: {}", property, !value.isEmpty());
        log.info("[extractProperty] Extracted value: {}", value);

        log.info("[extractProperty] Exiting method with value: {}", value);
        return value;
    }

    /**
     * Fetches a list of component names for the specified generated project.
     * <p>
     * This method retrieves components with their groups and returns only the
     * component names as a list.
     *
     * @param projectName the name of the project whose components are being fetched
     * @return a list of component names belonging to the project
     */
    @Override
    public List<String> fetchComponentsFromGeneratedProjects(String projectName) {
        log.info("[fetchComponentsFromGeneratedProjects] Starting fetch for project: {}", projectName);

        log.info("Fetching components for project: {}", projectName);
        Map<String, String> components = fetchComponentsWithGroups(projectName);

        log.info("Fetched {} components for project '{}'", components.size(), projectName);
        return new ArrayList<>(components.keySet());
    }

    /**
     * Fetches all components of a given project along with their component groups.
     * <p>
     * This method resolves the components directory for the specified project
     * and recursively fetches all components, mapping each component name to its group.
     * </p>
     *
     * @param projectName the name of the project whose components are being fetched
     * @return a {@link Map} where the key is the component name and the value is its group
     */
    @Override
    public Map<String, String> fetchComponentsWithGroups(String projectName) {
        log.info("[fetchComponentsWithGroups] Fetching components with groups for project: {}", projectName);

        String appId = AemUtil.getAppId(PROJECTS_DIR, projectName);
        log.info("[fetchComponentsWithGroups] Resolved App ID: {}", appId);

        Map<String, String> result = new LinkedHashMap<>();
        File componentsDir = new File(PROJECTS_DIR, projectName + CONTENT_ROOT_PATH + appId + "/" + COMPONENTS_FOLDER);

        log.info("[fetchComponentsWithGroups] Resolved components directory path: {}", componentsDir.getAbsolutePath());

        if (componentsDir.exists()) {
            log.info("[fetchComponentsWithGroups] Components directory found. Starting recursive fetch...");
            fetchRealComponentsRecursive(componentsDir, result);
            log.info("[fetchComponentsWithGroups] Completed fetching components. Total components found: {}", result.size());
        } else {
            log.info("[fetchComponentsWithGroups] Components directory does not exist for project: {}", projectName);
        }

        return result;
    }

    /**
     * Recursively traverses the components directory and fetches all valid AEM components.
     * <p>
     * This method scans the given directory, ignores excluded folders, reads the
     * `.content.xml` files to determine the component group, and adds each component
     * to the provided result map.
     * </p>
     *
     * @param dir    the directory to scan for components
     * @param result the map to store component names and their corresponding groups
     */
    private void fetchRealComponentsRecursive(File dir, Map<String, String> result) {
        if (!dir.isDirectory()) {
            log.info("Skipping non-directory path: {}", dir.getAbsolutePath());
            return;
        }

        String dirName = dir.getName();

        if (EXCLUDED_FOLDERS.stream().anyMatch(ex ->
                dirName.equalsIgnoreCase(ex) || dirName.startsWith(ex))) {
            log.info("Skipping excluded folder: {}", dirName);
            return;
        }

        File contentXml = new File(dir, CONTENT_XML); // ".content.xml"
        if (contentXml.exists()) {
            String content = FileGenerationUtil.readFile(contentXml);

            if (content.contains(DESIGN_DIALOG) ||
                    content.contains(AUTHORING_DIALOG_PATH)) {
                log.info("Skipping design dialog for component: {}", dirName);
                return;
            }

            String group = extractProperty(content, COMPONENT_GROUP).trim();
            result.put(dirName, group);
            log.info("[fetchRealComponentsRecursive] Discovered component: '{}' with group: '{}'", dirName, group);
        }

        File[] subDirs = dir.listFiles(File::isDirectory);
        if (subDirs != null) {
            for (File subDir : subDirs) {
                fetchRealComponentsRecursive(subDir, result);
            }
        }
    }

    /**
     * Retrieves all AEM components available in the classpath.
     * <p>
     * This method scans the specified classpath pattern and returns a list of all
     * component filenames found. Useful for populating available components for
     * project creation or editing.
     * </p>
     *
     * @return a list of all AEM component filenames found in the classpath
     * @throws IOException if there is an issue accessing resources in the classpath
     */
    @Override
    public List<String> getAllComponents() throws IOException {
        List<String> components = new ArrayList<>();
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();

        log.info("[getAllComponents] Scanning for AEM components under classpath: {}", COMPONENTS_CLASSPATH_PATTERN);
        Resource[] resources = resolver.getResources(COMPONENTS_CLASSPATH_PATTERN);

        for (Resource resource : resources) {
            String fileName = resource.getFilename();
            if (fileName != null) {
                components.add(fileName);
                log.debug("[getAllComponents] Discovered component resource: {}", fileName);
            }
        }

        log.info("[getAllComponents] Total AEM components discovered: {}", components.size());
        return components;
    }

    /**
     * Finds and returns the common components between all available components
     * and the components already present in a specific project.
     *
     * @param allComponents     the list of all available components
     * @param projectComponents the list of components already present in the project
     * @return a list of component names that are common to both input lists
     */
    @Override
    public List<String> getCommonComponents(List<String> allComponents, List<String> projectComponents) {
        log.info("[getCommonComponents] Finding common components. Total available: {}, Project components: {}",
                allComponents.size(), projectComponents.size());

        List<String> common = allComponents.stream()
                .filter(projectComponents::contains)
                .collect(Collectors.toList());

        log.info("[getCommonComponents] Common components found: {}", common.size());
        log.info("[getCommonComponents] Common components list: {}", common);

        return common;
    }

    /**
     * Finds and returns components that are present in all available components
     * but not yet included in the specified project.
     *
     * @param allComponents     the list of all available components
     * @param projectComponents the list of components already present in the project
     * @return a list of component names that are distinct (not in the project)
     */
    @Override
    public List<String> getDistinctComponents(List<String> allComponents, List<String> projectComponents) {
        log.info("[getDistinctComponents] Finding distinct components. Total available: {}, Project components: {}",
                allComponents.size(), projectComponents.size());

        List<String> distinct = allComponents.stream()
                .filter(c -> !projectComponents.contains(c))
                .collect(Collectors.toList());

        log.info("[getDistinctComponents] Distinct components found: {}", distinct.size());
        log.debug("[getDistinctComponents] Distinct components list: {}", distinct);

        return distinct;
    }


    /**
     * Retrieves a list of existing generated projects from the workspace.
     * <p>
     * This method checks the default generated projects directory and returns
     * the names of all existing projects. If the directory does not exist or
     * is empty, it returns an empty list.
     * </p>
     *
     * @return a list of existing project names; empty if none are found
     */
    @Override
    public List<String> getExistingProjects() {
        String projectsPath = System.getProperty(USER_DIR_SYS_PROP) + GENERATED_PROJECTS_DIR;
        File projectsDir = new File(projectsPath);

        log.info("[getExistingProjects] Checking for existing projects in: {}", projectsPath);

        if (!projectsDir.exists() || !projectsDir.isDirectory()) {
            log.warn("[getExistingProjects] No 'generated-projects' directory found at path: {}", projectsPath);
            return Collections.emptyList();
        }

        String[] names = projectsDir.list();
        if (names == null || names.length == 0) {
            log.info("[getExistingProjects] No existing projects found in directory: {}", projectsPath);
            return Collections.emptyList();
        }

        List<String> existingProjects = Arrays.asList(names);
        log.info("[getExistingProjects] Found {} existing projects.", existingProjects.size());
        log.debug("[getExistingProjects] Existing projects list: {}", existingProjects);

        return existingProjects;
    }

    /**
     * Loads a component's metadata and dialog fields for a given project and component name.
     * <p>
     * This method reads the component's `.content.xml` to retrieve its group and super type,
     * then parses the dialog XML under `_cq_dialog/.content.xml` to collect all dialog fields.
     * </p>
     *
     * @param projectName   the name of the project containing the component
     * @param componentName the name of the component to load
     * @return a {@link ComponentRequest} object populated with the component's metadata and fields
     */
    @Override
    public ComponentRequest loadComponent(String projectName, String componentName) {
        log.info("[loadComponent] Starting loadComponent for project='{}', component='{}'", projectName, componentName);

        String basePath = findComponentPathExact(projectName, componentName);
        String group = "";
        String superType = null;
        List<ComponentField> fields = new ArrayList<>();

        try {
            File contentXml = new File(basePath, CONTENT_XML);
            if (contentXml.exists()) {
                log.debug("[loadComponent] Reading component metadata from '{}'", contentXml.getAbsolutePath());
                String content = FileGenerationUtil.readFile(contentXml);

                group = extractProperty(content, COMPONENT_GROUP).trim();
                superType = extractProperty(content, SLING_RESOURCE_SUPER_TYPE).trim();

                log.debug("[loadComponent] Parsed component metadata: group='{}', superType='{}'", group, superType);
            } else {
                log.warn("[loadComponent] .content.xml not found for component '{}'", componentName);
            }

            File dialogXml = new File(basePath + "/" + CQ_DIALOG + "/" + CONTENT_XML);
            if (dialogXml.exists()) {
                log.debug("[loadComponent] Parsing dialog XML at '{}'", dialogXml.getAbsolutePath());

                DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
                factory.setNamespaceAware(false); // disabling namespaces for easier parsing
                DocumentBuilder builder = factory.newDocumentBuilder();
                Document doc = builder.parse(dialogXml);

                collectFields(doc.getDocumentElement(), fields);
                log.info("[loadComponent] Collected {} field(s) for component '{}'", fields.size(), componentName);
            } else {
                log.warn("[loadComponent] Dialog XML not found for component '{}'", componentName);
            }

        } catch (Exception e) {
            log.error("[loadComponent] Failed to load component '{}'", componentName, e);
        }

        ComponentRequest req = new ComponentRequest();
        req.setProjectName(projectName);
        req.setComponentName(componentName);
        req.setComponentGroup(group);
        req.setSuperType(superType);
        req.setFields(fields);

        log.info("[loadComponent] Completed loadComponent for component '{}'", componentName);
        return req;
    }

    /**
     * Updates an existing component in the specified project by regenerating
     * its files based on the provided {@link ComponentRequest}.
     * <p>
     * The method first deletes the existing component directory, then regenerates
     * all files using {@link FileGenerationUtil#generateAllFiles(String, ComponentRequest)}.
     * </p>
     *
     * @param projectName the name of the project containing the component
     * @param request     the component request containing updated metadata and fields
     */
    @Override
    public void updateComponent(String projectName, ComponentRequest request) {
        String appId = AemUtil.getAppId(PROJECTS_DIR, projectName);

        String compPath = PROJECTS_DIR + "/" + projectName + "/" + CONTENT_ROOT_PATH + appId + COMPONENTS_FOLDER + request.getComponentName();

        log.info("[updateComponent] Updating component '{}' in project '{}'", request.getComponentName(), projectName);
        log.debug("[updateComponent] Resolved component path for update: {}", compPath);

        try {
            FileUtils.deleteDirectory(new File(compPath));
            log.info("[updateComponent] Deleted existing component directory: {}", compPath);
        } catch (IOException e) {
            log.warn("[updateComponent] Could not clean component folder before update for '{}'", request.getComponentName(), e);
        }

        FileGenerationUtil.generateAllFiles(projectName, request);
        log.info("[updateComponent] Regenerated component '{}' in project '{}'", request.getComponentName(), projectName);
    }

    /**
     * Deletes a component from a specified project along with its associated
     * folders, HTL files, Sling Models, multifield classes, and Java classes.
     * <p>
     * The method performs the following steps:
     * <ol>
     *     <li>Resolve the component folder path.</li>
     *     <li>Collect Sling Models from HTL files.</li>
     *     <li>Collect multifield names from component dialog.</li>
     *     <li>Determine Java classes to delete, including child classes.</li>
     *     <li>Delete the component folder.</li>
     *     <li>Delete the collected Java classes.</li>
     * </ol>
     * </p>
     *
     * @param projectName   the name of the project containing the component
     * @param componentName the name of the component to delete
     */
    @Override
    public void deleteComponent(String projectName, String componentName) {
        log.info("[deleteComponent] Deleting component '{}' from project '{}'", componentName, projectName);

        String compPath = findComponentPathExact(projectName, componentName);
        if (compPath == null) {
            log.warn("[deleteComponent] Component '{}' not found in project '{}'", componentName, projectName);
            return;
        }
        log.debug("[deleteComponent] Component folder resolved at '{}'", compPath);

        Set<String> slingModels = new HashSet<>();
        try {
            slingModels = collectSlingModelsFromHTLFolder(new File(compPath));
            log.debug("[deleteComponent] Sling Models found: {}", slingModels);
        } catch (IOException e) {
            log.error("[deleteComponent] Failed to read HTL files for component '{}'", componentName, e);
        }

        ComponentRequest req = loadComponent(projectName, componentName);
        Set<String> multifieldNames = req.getFields().stream()
                .filter(f -> TYPE_MULTIFIELD.equals(f.getFieldType()))
                .map(ComponentField::getFieldName)
                .collect(Collectors.toSet());
        log.debug("[deleteComponent] Multifield names collected: {}", multifieldNames);

        Set<String> javaClassesToDelete = new HashSet<>();
        Set<String> processedClasses = new HashSet<>();
        Path javaRoot = Paths.get(PROJECTS_DIR, projectName, JAVA_SRC_PATH);

        for (String fqcn : slingModels) {
            String simpleName = fqcn.substring(fqcn.lastIndexOf('.') + 1);
            try {
                collectClassAndChildren(javaRoot, simpleName, javaClassesToDelete, processedClasses, multifieldNames);
            } catch (IOException e) {
                log.error("[deleteComponent] Failed to collect child classes for '{}'", fqcn, e);
            }
        }
        log.debug("[deleteComponent] Java classes marked for deletion: {}", javaClassesToDelete);

        try {
            FileUtils.deleteDirectory(new File(compPath));
            log.info("[deleteComponent] Deleted component folder '{}'", compPath);
        } catch (IOException e) {
            log.error("[deleteComponent] Failed to delete component folder for '{}'", componentName, e);
        }

        try (Stream<Path> paths = Files.walk(javaRoot)) {
            paths.filter(p -> javaClassesToDelete.contains(p.getFileName().toString()))
                    .forEach(p -> {
                        try {
                            Files.deleteIfExists(p);
                            log.info("[deleteComponent] Deleted Java class '{}'", p);
                        } catch (IOException ex) {
                            log.error("[deleteComponent] Failed to delete Java class '{}'", p, ex);
                        }
                    });
        } catch (IOException e) {
            log.error("[deleteComponent] Failed to locate model classes in '{}'", javaRoot, e);
        }
    }

    /**
     * Recursively collects all Sling Models referenced in HTL files within the given directory.
     *
     * @param dir the root directory to scan for HTL files
     * @return a set of fully qualified Sling Model class names found in the HTL files
     * @throws IOException if there is an error reading any HTL file
     */
    private Set<String> collectSlingModelsFromHTLFolder(File dir) throws IOException {
        Set<String> slingModels = new HashSet<>();
        if (!dir.isDirectory()) {
            log.warn("[collectSlingModelsFromHTLFolder] Provided path '{}' is not a directory", dir.getAbsolutePath());
            return slingModels;
        }

        File[] files = dir.listFiles();
        if (files == null) return slingModels;

        for (File file : files) {
            if (file.isDirectory()) {
                log.debug("[collectSlingModelsFromHTLFolder] Entering directory '{}'", file.getAbsolutePath());
                slingModels.addAll(collectSlingModelsFromHTLFolder(file));
            } else if (file.isFile() && file.getName().endsWith(HTL_FILE_EXTENSION)) {
                log.debug("[collectSlingModelsFromHTLFolder] Processing HTL file '{}'", file.getAbsolutePath());
                slingModels.addAll(extractSlingModelsFromHTL(file));
            }
        }

        log.info("[collectSlingModelsFromHTLFolder] Collected Sling Models from folder '{}': {}", dir.getAbsolutePath(), slingModels);
        return slingModels;
    }


    /**
     * Extracts Sling Model class references from a single HTL file.
     * <p>
     * The method scans each line of the HTL file for {@code data-sly-use} attributes
     * and collects fully qualified class names that belong to project-specific packages.
     * </p>
     *
     * @param htlFile the HTL file to parse
     * @return a set of fully qualified Sling Model class names found in the HTL file
     * @throws IOException if there is an error reading the HTL file
     */
    private Set<String> extractSlingModelsFromHTL(File htlFile) throws IOException {
        Set<String> classes = new HashSet<>();
        log.debug("[extractSlingModelsFromHTL] Extracting Sling Models from HTL file '{}'", htlFile.getAbsolutePath());

        List<String> lines = Files.readAllLines(htlFile.toPath());
        for (String line : lines) {
            line = line.trim();
            if (line.contains(DATA_SLY_USE)) {
                int eqIndex = line.indexOf("=");
                if (eqIndex > 0) {
                    String ref = line.substring(eqIndex + 1)
                            .replaceAll("[\"';]", "")
                            .replaceAll("[/>].*$", "")
                            .trim();

                    if (ref.startsWith("com.")) {
                        classes.add(ref);
                        log.debug("[extractSlingModelsFromHTL] Found Sling Model reference: '{}'", ref);
                    }
                }
            }
        }

        log.info("[extractSlingModelsFromHTL] Extracted Sling Models from '{}': {}", htlFile.getName(), classes);
        return classes;
    }

    /**
     * Recursively collects a Java class and all its child classes.
     * <p>
     * Child classes are identified via {@code @ChildResource} annotations
     * or via multifield references in the parent class.
     * All identified class names are added to {@code javaClassesToDelete}.
     * </p>
     *
     * @param javaRoot            Root path of the Java source folder
     * @param className           Name of the class to process
     * @param javaClassesToDelete Set to store class names to be deleted
     * @param processedClasses    Set to avoid processing the same class multiple times
     * @param multifieldNames     Set of multifield variable names to track child dependencies
     * @throws IOException If reading the Java file fails
     */
    private void collectClassAndChildren(Path javaRoot, String className,
                                         Set<String> javaClassesToDelete,
                                         Set<String> processedClasses,
                                         Set<String> multifieldNames) throws IOException {

        if (processedClasses.contains(className)) {
            log.debug("Class '{}' already processed, skipping recursion", className);
            return;
        }
        processedClasses.add(className);

        Path classPath = findJavaClassRecursive(javaRoot, className + JAVA_EXTENSION);
        if (classPath == null) {
            log.warn("Java class '{}' not found under '{}'", className, javaRoot);
            return;
        }
        log.info("Found Java class '{}' at '{}'", className, classPath);

        javaClassesToDelete.add(classPath.getFileName().toString());

        List<String> lines = Files.readAllLines(classPath);

        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i).trim();

            if (line.contains(CHILD_RESOURCE_ANNOTATION)) {
                for (int j = i + 1; j < Math.min(i + 4, lines.size()); j++) {
                    String fieldLine = lines.get(j).trim();
                    Matcher m = Pattern.compile(CHILD_CLASS_FIELD_PATTERN).matcher(fieldLine);
                    if (m.find()) {
                        String childClass = m.group(1) != null ? m.group(1) :
                                m.group(2) != null ? m.group(2) : m.group(3);
                        if (childClass != null && !childClass.equals(className)) {
                            log.info("Found @ChildResource child class '{}' in '{}'", childClass, className);
                            collectClassAndChildren(javaRoot, childClass, javaClassesToDelete, processedClasses, multifieldNames);
                        }
                    }
                }
            }

            for (String mfName : multifieldNames) {
                if (line.matches(".*\\b([A-Z]\\w+)\\s+" + mfName + ";.*")) {
                    String childClassName = line.replaceAll(".*\\b([A-Z]\\w+)\\s+" + mfName + ";.*", "$1");
                    log.info("Found child class '{}' for multifield '{}' in '{}'", childClassName, mfName, className);
                    collectClassAndChildren(javaRoot, childClassName, javaClassesToDelete, processedClasses, multifieldNames);
                }
            }
        }
    }

    /**
     * Recursively searches for a Java class file by its simple name under the given root directory.
     * <p>
     * This method walks the directory tree starting from {@code javaRoot} and looks for a file
     * whose name exactly matches {@code className}. If found, it returns the {@link Path} to the file;
     * otherwise, returns {@code null}.
     * </p>
     *
     * @param javaRoot  the root directory under which to search (typically {@code core/src/main/java})
     * @param className the simple file name of the Java class to find (e.g., "MyComponentModel.java")
     * @return {@link Path} to the Java class file if found; {@code null} otherwise
     * @throws IOException if an I/O error occurs while walking the file tree
     */
    private Path findJavaClassRecursive(Path javaRoot, String className) throws IOException {
        log.info("Searching for Java class '{}' under '{}'", className, javaRoot);

        try (Stream<Path> paths = Files.walk(javaRoot)) {
            Path found = paths
                    .filter(p -> p.getFileName().toString().equals(className))
                    .findFirst()
                    .orElse(null);

            if (found != null) {
                log.info("Java class '{}' found at '{}'", className, found);
            } else {
                log.warn("Java class '{}' not found under '{}'", className, javaRoot);
            }
            return found;
        }
    }

    /**
     * Reads and returns the HTML content of a specific AEM component.
     *
     * @param projectName   the name of the project containing the component
     * @param componentName the name of the component
     * @return the HTML content as a {@link String}; empty string if reading fails
     */
    @Override
    public String getComponentHtml(String projectName, String componentName) {

        log.info("Fetching HTML for component '{}' in project '{}'", componentName, projectName);

        String componentPathExact = findComponentPathExact(projectName, componentName);
        if (componentPathExact == null) {
            log.warn("Component '{}' not found in project '{}'", componentName, projectName);
            return "";
        }

        Path htmlPath = Paths.get(componentPathExact, componentName + HTL_FILE_EXTENSION);

        try {
            String htmlContent = Files.readString(htmlPath);
            log.info("Successfully read HTML for component '{}'", componentName);
            return htmlContent;
        } catch (IOException e) {
            log.error("Failed to read HTML for component '{}' at '{}'", componentName, htmlPath, e);
            return "";
        }
    }

    /**
     * Reads and returns the Java code of the Sling Model(s) used in a specific AEM component.
     * It recursively collects the main model and potential child classes referenced in the code.
     *
     * @param projectName   the name of the project containing the component
     * @param componentName the name of the component
     * @return concatenated Java source code as a {@link String}; returns comment if HTL or model is missing
     */
    @Override
    public String getComponentJava(String projectName, String componentName) {
        log.info("Fetching Sling Model Java code for component '{}' in project '{}'", componentName, projectName);

        try {
            String htlContent = getComponentHtml(projectName, componentName);

            if (htlContent == null || htlContent.isEmpty()) {
                log.warn("No HTL found for component '{}'", componentName);
                return "// No HTL found for component: " + componentName;
            }

            Pattern pattern = Pattern.compile(DATA_SLY_USE_PATTERN);
            Matcher matcher = pattern.matcher(htlContent);

            String modelClass = null;
            while (matcher.find()) {
                String candidate = matcher.group(1);
                if (!candidate.endsWith(HTL_FILE_EXTENSION) && candidate.contains(".")) {
                    modelClass = candidate;
                    break;
                }
            }

            if (modelClass == null) {
                log.warn("No Sling Model binding found in HTL for component '{}'", componentName);
                return "// No Sling Model binding found in HTL for: " + componentName;
            }

            StringBuilder result = new StringBuilder();
            Set<String> processed = new HashSet<>();
            Deque<String> stack = new ArrayDeque<>();
            stack.push(modelClass);

            Path javaRoot = Paths.get(PROJECTS_DIR, projectName, JAVA_SRC_PATH);

            while (!stack.isEmpty()) {
                String current = stack.pop();

                if (!processed.add(current)) continue;

                Path javaFile = javaRoot.resolve(current.replace(".", "/") + JAVA_EXTENSION);
                if (!Files.exists(javaFile)) continue;

                String code = Files.readString(javaFile);
                String simpleName = current.substring(current.lastIndexOf(".") + 1);
                String packageName = current.substring(0, current.lastIndexOf("."));

                result.append("// -------------------------------------------------\n")
                        .append(simpleName).append(" =====\n\n")
                        .append(code).append("\n\n");

                Matcher refMatcher = Pattern.compile(JAVA_CLASS_REF_PATTERN).matcher(code);
                while (refMatcher.find()) {
                    String refClass = refMatcher.group(1);
                    if (!refClass.equals(simpleName)) {
                        stack.push(packageName + "." + refClass);
                    }
                }
            }

            log.info("Successfully fetched Java code for component '{}'", componentName);
            return result.toString();

        } catch (Exception e) {
            log.error("Failed while resolving Sling Model for component '{}'", componentName, e);
            return "// Error while resolving Sling Model: " + e.getMessage();
        }
    }

    private static String capitalize(String input) {
        return (input == null || input.isEmpty()) ? input
                : input.substring(0, 1).toUpperCase() + input.substring(1);
    }

    /**
     * Determines the field type corresponding to a given AEM resource type.
     * <p>
     * This method iterates through all {@link FieldType} enum values and performs
     * a reverse lookup based on the resource type. If multiple enums match,
     * the last match takes precedence.
     * </p>
     *
     * @param resourceType the AEM resource type (e.g., "cq/gui/components/authoring/dialog")
     * @return the corresponding field type as a string, or an empty string if not found
     */
    private String getFieldTypeFromResource(String resourceType) {
        if (resourceType == null || resourceType.isEmpty()) {
            log.warn("Resource type is null or empty, returning empty string");
            return "";
        }

        String fieldType = "";
        for (FieldType ft : FieldType.values()) {
            if (ft.getResourceType().equals(resourceType)) {
                fieldType = ft.getType();
                log.debug("Matched resourceType '{}' to fieldType '{}'", resourceType, fieldType);
            }
        }

        if (fieldType.isEmpty()) {
            log.info("No matching fieldType found for resourceType '{}'", resourceType);
        }

        return fieldType;
    }

    /**
     * Determines the field type for a given XML element from the component dialog.
     * <p>
     * Checks the element's {@code sling:resourceType} and certain attributes to
     * categorize it into a field type like "multifield", "multiselect", "fileupload", or "image".
     * </p>
     *
     * @param elem the XML element representing a dialog field
     * @return the determined field type as a string, or the default from {@link #getFieldTypeFromResource(String)}
     */
    private String determineFieldType(Element elem) {
        String resourceType = elem.getAttribute(SLING_RESOURCE_TYPE);
        String type = getFieldTypeFromResource(resourceType);

        log.debug("Determining field type for resourceType '{}'", resourceType);

        if (GRANITE_MULTIFIELD.equals(resourceType)) {
            type = TYPE_MULTIFIELD;
            log.debug("Detected multifield for element '{}'", elem.getNodeName());
        }

        else if (GRANITE_SELECT.equals(resourceType)
                && "true".equalsIgnoreCase(elem.getAttribute("multiple"))) {
            type = TYPE_MULTISELECT;
            log.debug("Detected multiselect for element '{}'", elem.getNodeName());
        }
        else if (CQ_FILEUPLOAD.equals(resourceType)) {
            String node = elem.getNodeName().toLowerCase();
            if (node.contains("file")) {
                type = FILEUPLOAD;
            } else if (node.contains(IMAGE)) {
                type = IMAGE;
            }
            log.debug("Detected '{}' upload for element '{}'", type, elem.getNodeName());
        }

        log.info("Final field type for element '{}': '{}'", elem.getNodeName(), type);
        return type;
    }

    /**
     * Parses a dialog XML element into one or more {@link ComponentField} objects.
     * <p>
     * Handles multifields, selects, multiselects, radiogroups, file/image uploads, and tabs.
     * Recurses into nested items for multifields and tabs.
     * </p>
     *
     * @param elem the XML element representing a dialog field
     * @return a list of parsed {@link ComponentField} objects
     */
    private List<ComponentField> parseField(Element elem) {
        List<ComponentField> result = new ArrayList<>();

        String fieldLabel = elem.getAttribute(ATTR_FIELD_LABEL);
        String nameAttr = elem.getAttribute(KEY_NAME);
        String fileRefAttr = elem.getAttribute(ATTR_FILE_REFERENCE);
        String resourceType = elem.getAttribute(SLING_RESOURCE_TYPE);
        String fieldType = getFieldTypeFromResource(resourceType);

        String fieldName = null;

        log.debug("Parsing field element '{}', resourceType='{}'", elem.getNodeName(), resourceType);

        if (TYPE_MULTIFIELD.equals(fieldType) && (nameAttr == null || nameAttr.isBlank())) {
            NodeList fieldNodes = elem.getElementsByTagName(ATTR_FIELD);
            if (fieldNodes.getLength() > 0) {
                Element fieldElem = (Element) fieldNodes.item(0);
                String nestedFieldName = fieldElem.getAttribute(KEY_NAME);
                if (nestedFieldName != null && !nestedFieldName.isBlank()) {
                    fieldName = nestedFieldName.startsWith("./") ? nestedFieldName.substring(2) : nestedFieldName;
                }
            }
            log.debug("Resolved multifield name: '{}'", fieldName);
        }

        if (fieldName == null && nameAttr != null && !nameAttr.isBlank()) {
            if (FILEUPLOAD.equals(fieldType) && fileRefAttr != null && !fileRefAttr.isBlank()) {
                fieldName = fileRefAttr.startsWith("./") ? fileRefAttr.substring(2) : fileRefAttr;
            } else {
                fieldName = nameAttr.startsWith("./") ? nameAttr.substring(2) : nameAttr;
            }
        }

        log.info("Field parsed: label='{}', name='{}', type='{}', resourceType='{}'",
                fieldLabel, fieldName, fieldType, resourceType);

        List<OptionItem> options = null;
        List<ComponentField> nested = null;

        if (TYPE_MULTIFIELD.equals(fieldType)) {
            nested = new ArrayList<>();
            NodeList fieldNodes = elem.getElementsByTagName(ITEMS);
            if (fieldNodes.getLength() > 0) {
                Element itemsElem = (Element) fieldNodes.item(0);
                collectFields(itemsElem, nested);
            }
        }
        else if (SELECT.equals(fieldType) || TYPE_MULTISELECT.equals(fieldType) || RADIOGROUP.equals(fieldType)) {
            if (SELECT.equals(fieldType)) {
                String multipleAttr = elem.getAttribute(ATTR_MULTIPLE);
                if (VALUE_TRUE.equalsIgnoreCase(multipleAttr)) {
                    fieldType = TYPE_MULTISELECT;
                }
            }
            NodeList itemsNodes = elem.getElementsByTagName(ITEMS);
            if (itemsNodes.getLength() > 0) {
                Element itemsElem = (Element) itemsNodes.item(0);
                options = new ArrayList<>();
                NodeList optionNodes = itemsElem.getChildNodes();
                for (int i = 0; i < optionNodes.getLength(); i++) {
                    Node n = optionNodes.item(i);
                    if (n instanceof Element optionElem) {
                        String text = optionElem.getAttribute(ATTR_TEXT);
                        String value = optionElem.getAttribute(ATTR_VALUE);
                        if ((text != null && !text.isBlank()) || (value != null && !value.isBlank())) {
                            options.add(new OptionItem(text, value));
                        }
                    }
                }
            }
        }

        else if (TYPE_TABS.equals(fieldType)) {
            String jcrTitle = elem.getAttribute(JCR_TITLE);
            if (jcrTitle != null && !jcrTitle.isBlank()) {
                fieldLabel = jcrTitle;
            }
            String tabName = elem.getNodeName();

            nested = new ArrayList<>();
            NodeList childNodes = elem.getChildNodes();
            for (int i = 0; i < childNodes.getLength(); i++) {
                Node n = childNodes.item(i);
                if (n instanceof Element childElem && ITEMS.equals(childElem.getNodeName())) {
                    collectFields(childElem, nested);
                }
            }

            result.add(new ComponentField(fieldLabel, tabName, TYPE_TABS, false, nested, null));
            return result;
        }

        result.add(new ComponentField(fieldLabel, fieldName, fieldType, false, nested, options));
        return result;
    }

    /**
     * Recursively collects dialog fields from the given parent XML element.
     * Skips technical containers but continues traversing into their children.
     *
     * @param parent the parent XML element (dialog node) to parse
     * @param fields the list to accumulate parsed ComponentField objects
     */
    private void collectFields(Element parent, List<ComponentField> fields) {
        NodeList children = parent.getChildNodes();
        log.info("Collecting fields from parent node '{}', child count={}", parent.getNodeName(), children.getLength());

        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (!(node instanceof Element elem)) {
                continue;
            }

            String resourceType = elem.getAttribute(SLING_RESOURCE_TYPE);
            String type = determineFieldType(elem);

            if (GRANITE_CONTAINER.equals(resourceType)) {
                String parentResourceType = getParentResourceType(elem);

                log.info("Checking container: nodeName='{}', resourceType='{}', parentResourceType='{}'",
                        elem.getNodeName(), resourceType, parentResourceType);

                if (!GRANITE_TABS.equals(parentResourceType)) {
                    log.info("Skipping container '{}' as field, but parsing its children", elem.getNodeName());
                    collectFields(elem, fields);
                    continue;
                }
            }

            if (!type.isEmpty()) {
                List<ComponentField> parsed = parseField(elem);
                if (parsed != null && !parsed.isEmpty()) {
                    log.info("Adding {} parsed field(s) from node '{}'", parsed.size(), elem.getNodeName());
                    fields.addAll(parsed);
                } else {
                    log.info("No valid fields parsed from node '{}'", elem.getNodeName());
                }
            } else {
                log.info("Recursing into children of node '{}' with unknown or empty type", elem.getNodeName());
                collectFields(elem, fields);
            }
        }
    }

    /**
     * Finds the nearest ancestor element that defines a sling:resourceType.
     * Useful for cases where wrapper nodes (e.g., <items>) don't have their own resourceType.
     *
     * @param elem the starting XML element
     * @return the sling:resourceType of the nearest ancestor, or empty string if none found
     */
    private String getParentResourceType(Element elem) {
        Node parent = elem.getParentNode();
        while (parent instanceof Element parentElem) {
            if (parentElem.hasAttribute(SLING_RESOURCE_TYPE)) {
                String resourceType = parentElem.getAttribute(SLING_RESOURCE_TYPE);
                log.info("Found parent resourceType '{}' at node '{}'", resourceType, parentElem.getNodeName());
                return resourceType;
            }
            parent = parent.getParentNode();
        }
        log.info("No parent resourceType found for node '{}'", elem.getNodeName());
        return "";
    }


    /**
     * Retrieves a list of all component names for a given project.
     *
     * @param projectName the name of the project
     * @return list of component names (empty list if none found)
     */
    @Override
    public List<String> getProjectComponentsMap(String projectName) {
        Map<String, String> components = fetchComponentsWithGroups(projectName);
        if (components == null || components.isEmpty()) {
            log.warn("No components found for project '{}'", projectName);
            return Collections.emptyList();
        }
        return new ArrayList<>(components.keySet());
    }

    /**
     * Adds the selected AEM components to an existing project by copying them into the project's
     * content folder. This does not overwrite existing components unless explicitly handled in
     * copySelectedComponents().
     *
     * @param projectName        the name of the target project
     * @param selectedComponents list of component names to add
     */
    @Override
    public void addComponentsToExistingProject(String projectName, List<String> selectedComponents) {
        log.info("Adding {} components to existing project '{}'", selectedComponents.size(), projectName);

        try {

            String baseDir = System.getProperty(USER_DIR_SYS_PROP) + "/" + PROJECTS_DIR + "/";
            String appId = AemUtil.getAppId(PROJECTS_DIR, projectName);

            String contentFolderPath = baseDir + projectName + CONTENT_ROOT_PATH +
                    appId + "/" + COMPONENTS_FOLDER;

            log.info("Copying components {} to '{}'", selectedComponents, contentFolderPath);

            copySelectedComponents(selectedComponents, contentFolderPath, projectName);

            log.info("Successfully added components to project '{}'", projectName);

        } catch (Exception e) {
            log.error("Error while adding components to project '{}'", projectName, e);
        }
    }

    /**
     * Copies the selected AEM components into the project.
     * <p>
     * - Copies component files from source folder.
     * - Updates .content.xml sling:resourceType.
     * - Updates HTL files to reference the correct Sling Model.
     * - Copies the Sling Model and dependencies.
     * </p>
     *
     * @param selectedComponents List of component names to copy
     * @param targetPath         Target folder path in project
     * @param projectName        Project name
     */
    @Override
    public void copySelectedComponents(List<String> selectedComponents, String targetPath, String projectName) {
        if (selectedComponents == null || selectedComponents.isEmpty()) {
            log.warn("No components selected to copy for project '{}'", projectName);
            return;
        }

        String slingModelsSourcePath = System.getProperty(USER_DIR_SYS_PROP) + "/" + SLING_MODELS_SOURCE;
        Path javaSourceRoot = Paths.get(PROJECTS_DIR, projectName, JAVA_SRC_PATH);

        Path modelPath = findModelBasePath(javaSourceRoot);
        log.info("Model path found: {}", modelPath);

        String modelBasePath = modelPath.toString();
        String packageName = javaSourceRoot.relativize(modelPath).toString().replace(File.separatorChar, '.');

        Set<String> copiedModels = new HashSet<>();

        for (String component : selectedComponents) {
            try {
                File source = new File(AEM_COMPONENTS_SOURCE + "/" + component);
                File destination = new File(targetPath + "/" + component);

                if (!source.exists()) {
                    log.warn("Source component not found: {}", source.getAbsolutePath());
                    continue;
                }

                if (destination.exists()) {
                    FileUtils.deleteDirectory(destination);
                    log.info("Deleted existing component folder: {}", destination.getAbsolutePath());
                }

                FileUtils.copyDirectory(source, destination);
                log.info("Copied component '{}' to '{}'", component, destination.getAbsolutePath());

                File contentXml = new File(destination, CONTENT_XML);
                if (contentXml.exists()) {
                    String content = FileUtils.readFileToString(contentXml, UTF_8);
                    content = content.replaceAll(SLING_RESOURCE_TYPE_PATTERN,
                            SLING_RESOURCE_TYPE_ATTR + projectName + "/" + COMPONENTS_FOLDER + "/" + component.toLowerCase() + "\"");
                    FileUtils.writeStringToFile(contentXml, content, UTF_8);
                    log.info("Updated sling:resourceType in {}", contentXml.getAbsolutePath());
                }

                File html = new File(destination, component + HTL_FILE_EXTENSION);
                File parentModel = findMatchingModelFile(slingModelsSourcePath, component);
                log.info("ParentModel......{}", parentModel);
                if (html.exists() && parentModel != null) {
                    String htmlContent = FileUtils.readFileToString(html, UTF_8);
                    String fqcn = extractFullyQualifiedClassName(parentModel, packageName);
                    if (fqcn != null) {
                        htmlContent = htmlContent.replaceAll(DATA_SLY_USE_MODEL_PATTERN,
                                DATA_SLY_USE_MODEL + fqcn + "\"");
                        FileUtils.writeStringToFile(html, htmlContent, UTF_8);
                        log.info("Updated HTL model reference in {}", html.getAbsolutePath());
                    }
                }

                if (parentModel != null && parentModel.exists()) {
                    copyModelAndDependencies(parentModel, slingModelsSourcePath, modelBasePath, packageName, copiedModels);
                    log.info("Copied Sling Model and dependencies for component '{}'", component);
                } else {
                    log.warn("No matching Sling Model found for component '{}'", component);
                }

            } catch (IOException e) {
                log.error("Failed to process component '{}'", component, e);
            }
        }
    }

    /**
     * Recursively searches for the first "models" directory under the given Java source root.
     * <p>
     * This is typically used to locate the base package for Sling Models within the project's
     * Java source directory.
     * </p>
     *
     * @param javaSourceRoot The root path of the Java source folder (e.g., core/src/main/java)
     * @return Path to the "models" directory
     * @throws RuntimeException if the "models" directory is not found or an I/O error occurs
     */
    private static Path findModelBasePath(Path javaSourceRoot) {
        log.info("Searching for 'models' directory under Java source root: {}", javaSourceRoot);

        try (Stream<Path> paths = Files.walk(javaSourceRoot)) {

            Optional<Path> modelPath = paths
                    .filter(Files::isDirectory) // Only consider directories
                    .filter(p -> p.getFileName().toString().equals(MODELS_FOLDER)) // Match folder named 'models'
                    .findFirst();

            if (modelPath.isPresent()) {
                log.info("'models' directory found at: {}", modelPath.get());
                return modelPath.get();
            } else {
                String errorMsg = "models directory not found under: " + javaSourceRoot;
                log.error(errorMsg);
                throw new IOException(errorMsg);
            }

        } catch (IOException e) {
            log.error("Error while searching for 'models' directory under {}", javaSourceRoot, e);
            throw new RuntimeException(e);
        }
    }


    /**
     * Finds the Java Sling Model file corresponding to a given component.
     * <p>
     * The method first attempts an exact match using the pattern
     * "{componentName}Model.java" (case-insensitive). If not found, it falls back
     * to a partial match where the file name contains the component name and ends with "Model.java".
     * </p>
     *
     * @param modelsDirPath The absolute path to the directory containing Sling Model Java files.
     * @param componentName The component name for which the model is being searched.
     * @return The File object representing the matching Java model, or null if none is found.
     */
    private File findMatchingModelFile(String modelsDirPath, String componentName) {
        log.info("Searching for Sling Model for component '{}' in directory '{}'", componentName, modelsDirPath);

        File dir = new File(modelsDirPath);
        if (!dir.exists() || !dir.isDirectory()) {
            log.warn("Models directory '{}' does not exist or is not a directory", modelsDirPath);
            return null;
        }

        File[] files = dir.listFiles((d, name) -> name.endsWith(JAVA_EXTENSION));
        if (files == null || files.length == 0) {
            log.warn("No Java files found in models directory '{}'", modelsDirPath);
            return null;
        }

        String lcComponent = componentName.toLowerCase();

        for (File file : files) {
            String lcFile = file.getName().toLowerCase();
            if (lcFile.equals(lcComponent + MODEL_FILE_SUFFIX)) {
                log.info("Exact match found for component '{}' → '{}'", componentName, file.getName());
                return file;
            }
        }

        for (File file : files) {
            String lcFile = file.getName().toLowerCase();
            if (lcFile.contains(lcComponent) && lcFile.endsWith(MODEL_FILE_SUFFIX)) {
                log.info("Partial match found for component '{}' → '{}'", componentName, file.getName());
                return file;
            }
        }

        log.warn("No matching Sling Model found for component '{}'", componentName);
        return null;
    }

    /**
     * Copies a Sling Model Java file to the target location, updating the package
     * and internal imports, and recursively copies any dependent model classes.
     *
     * @param modelFile         The Java file representing the Sling Model to copy.
     * @param sourceBase        The source directory containing the original models.
     * @param targetBase        The target directory where the models should be copied.
     * @param targetPackageName The Java package name to apply in the copied model.
     * @param copiedModels      A set of model names that have already been copied to avoid duplication.
     * @throws IOException If reading or writing files fails.
     */
    private void copyModelAndDependencies(File modelFile, String sourceBase, String targetBase, String targetPackageName, Set<String> copiedModels) throws IOException {
        if (modelFile == null || !modelFile.exists()) {
            log.warn("Model file is null or does not exist: {}", modelFile);
            return;
        }

        String modelName = modelFile.getName();
        if (copiedModels.contains(modelName)) {
            log.info("Model '{}' already copied, skipping", modelName);
            return;
        }

        log.info("Copying Sling Model '{}'", modelName);

        String originalContent = FileUtils.readFileToString(modelFile, UTF_8);

        String content = originalContent.replaceFirst(
                PACKAGE_DECLARATION_REGEX,
                "package " + targetPackageName + ";"
        );

        Pattern importPattern = Pattern.compile(IMPORT_STATEMENT_REGEX);
        Matcher importMatcher = importPattern.matcher(content);
        StringBuffer updatedContent = new StringBuffer();
        while (importMatcher.find()) {
            String className = importMatcher.group(1);
            String newImport = "import " + targetPackageName + "." + className + ";";
            importMatcher.appendReplacement(updatedContent, Matcher.quoteReplacement(newImport));
            log.info("Updated import for '{}' in model '{}'", className, modelName);
        }
        importMatcher.appendTail(updatedContent);
        content = updatedContent.toString();

        File destFile = new File(targetBase, modelFile.getName());
        destFile.getParentFile().mkdirs();
        FileUtils.writeStringToFile(destFile, content, UTF_8);
        copiedModels.add(modelName);
        log.info("Sling Model '{}' copied to '{}'", modelName, destFile.getAbsolutePath());

        Set<String> dependentTypes = extractReferencedModelTypes(originalContent);
        for (String type : dependentTypes) {
            File depFile = new File(sourceBase, type + JAVA_EXTENSION);
            if (depFile.exists()) {
                log.info("Copying dependent model '{}' for '{}'", type, modelName);
                copyModelAndDependencies(depFile, sourceBase, targetBase, targetPackageName, copiedModels);
            } else {
                log.warn("Dependent model '{}' not found for '{}'", type, modelName);
            }
        }
    }

    /**
     * Extracts referenced Sling Model class names from a given Java source content.
     * This includes classes referenced via imports and direct usage in the code.
     *
     * @param content The Java source content as a string.
     * @return A set of Sling Model class names referenced in the content.
     */
    private Set<String> extractReferencedModelTypes(String content) {
        Set<String> types = new HashSet<>();

        Pattern importPattern = Pattern.compile(IMPORT_STATEMENT_REGEX);
        Matcher importMatcher = importPattern.matcher(content);
        while (importMatcher.find()) {
            String type = importMatcher.group(1);
            types.add(type);
            log.info("Found referenced model via import: {}", type);
        }

        File modelsDir = new File(System.getProperty(USER_DIR_SYS_PROP) + "/" + SLING_MODELS_SOURCE);

        if (modelsDir.exists() && modelsDir.isDirectory()) {
            File[] modelFiles = modelsDir.listFiles((dir, name) -> name.endsWith(JAVA_EXTENSION));
            if (modelFiles != null) {
                for (File modelFile : modelFiles) {
                    String className = modelFile.getName().replace(JAVA_EXTENSION, "");
                    Pattern usagePattern = Pattern.compile("\\b" + className + "\\b");
                    Matcher usageMatcher = usagePattern.matcher(content);
                    if (usageMatcher.find()) {
                        types.add(className);
                        log.info("Found referenced model via usage: {}", className);
                    }
                }
            }
        } else {
            log.warn("Sling Models directory not found: {}", modelsDir.getAbsolutePath());
        }

        return types;
    }

    /**
     * Extracts the fully qualified class name (FQCN) from a Java file.
     * Uses the target package name to construct the FQCN.
     *
     * @param javaFile      The Java source file
     * @param targetPackage The package name to prepend
     * @return Fully qualified class name, or null if not found
     */
    private String extractFullyQualifiedClassName(File javaFile, String targetPackage) {
        if (javaFile == null || !javaFile.exists()) {
            log.warn("Java file does not exist: {}", javaFile);
            return null;
        }

        try {
            String content = FileUtils.readFileToString(javaFile, "UTF-8");
            Pattern classPattern = Pattern.compile(CLASS_DECLARATION_REGEX);
            Matcher matcher = classPattern.matcher(content);

            if (matcher.find()) {
                String className = matcher.group(1);
                String fqcn = targetPackage + "." + className;
                log.info("Extracted FQCN '{}' from file '{}'", fqcn, javaFile.getAbsolutePath());
                return fqcn;
            } else {
                log.warn("No public class found in file '{}'", javaFile.getAbsolutePath());
            }
        } catch (IOException e) {
            log.error("Failed to extract FQCN from file '{}'", javaFile.getAbsolutePath(), e);
        }
        return null;
    }

    /**
     * Fetches all component groups for a given project.
     * <p>
     * This method scans the project's components folder recursively and collects all
     * component groups. It excludes technical groups like Structure, hidden, or Form groups.
     *
     * @param projectName the name of the project
     * @return a list of component groups; if none found, returns a list with the project name
     */
    @Override
    public List<String> getComponentGroups(String projectName) {
        log.info("Starting to fetch component groups for project '{}'", projectName);

        String appTitle = readAppTitleFromPom(projectName);
        if (appTitle == null || appTitle.isBlank()) {
            log.info("App title not found in POM, using project name '{}'", projectName);
            appTitle = projectName;
        }
        String appId = AemUtil.getAppId(PROJECTS_DIR, projectName);

        String componentsPath = PROJECTS_DIR + "/" + projectName + CONTENT_ROOT_PATH + appId + "/" + COMPONENTS_FOLDER;
        log.info("Components folder path resolved to '{}'", componentsPath);

        File folder = new File(componentsPath);
        Set<String> groups = new HashSet<>();
        groups.add(appTitle);

        if (folder.exists() && folder.isDirectory()) {
            log.info("Collecting component groups from folder '{}'", componentsPath);
            collectComponentGroupsRecursive(folder, groups);
            log.info("Groups collected (before filtering): {}", groups);
        } else {
            log.warn("Components folder '{}' does not exist for project '{}'", componentsPath, projectName);
        }

        final String finalAppTitle = appTitle;
        groups.removeIf(g -> {
            String trimmed = g.trim();
            boolean remove = trimmed.equals(finalAppTitle + STRUCTURE_GROUP_SUFFIX)
                    || trimmed.equals(HIDDEN_GROUP)
                    || trimmed.contains(FORM_GROUP_SUFFIX);
            if (remove) {
                log.debug("Excluding group '{}' as technical or hidden", trimmed);
            }
            return remove;
        });
        log.info("Groups after filtering: {}", groups);

        List<String> result = groups.isEmpty() ? List.of(appTitle) : new ArrayList<>(groups);
        log.info("Final component groups for project '{}': {}", projectName, result);

        return result;
    }

    /**
     * Recursively collects component groups from a directory.
     * Skips technical or excluded folders like _cq_, hidden, backup, etc.
     *
     * @param dir    the current folder to scan
     * @param groups the set of collected component groups
     */
    private void collectComponentGroupsRecursive(File dir, Set<String> groups) {
        if (!dir.isDirectory()) {
            log.debug("Skipping non-directory: {}", dir.getAbsolutePath());
            return;
        }

        String dirName = dir.getName();
        if (EXCLUDED_FOLDERS.stream().anyMatch(ex -> dirName.equalsIgnoreCase(ex) || dirName.startsWith(ex))) {
            log.debug("Skipping excluded folder '{}'", dir.getAbsolutePath());
            return;
        }

        File contentXml = new File(dir, CONTENT_XML);
        if (contentXml.exists()) {
            String content = FileGenerationUtil.readFile(contentXml);
            String group = extractProperty(content, COMPONENT_GROUP).trim();
            if (!group.isEmpty()) {
                log.info("Found component group '{}' in folder '{}'", group, dir.getAbsolutePath());
                groups.add(group); // add all groups; filtering is done later
            } else {
                log.debug("No componentGroup property found in '{}'", contentXml.getAbsolutePath());
            }
        }

        File[] subDirs = dir.listFiles(File::isDirectory);
        if (subDirs != null) {
            for (File subDir : subDirs) {
                collectComponentGroupsRecursive(subDir, groups);
            }
        } else {
            log.debug("No subdirectories found in '{}'", dir.getAbsolutePath());
        }
    }

    /**
     * Reads the app title (componentGroupName) from a project's pom.xml file.
     *
     * @param projectName the project folder name
     * @return the componentGroupName from pom.xml, or null if not found
     */
    public String readAppTitleFromPom(String projectName) {
        File pom = new File(PROJECTS_DIR + "/" + projectName + "/" + POM_FILE_NAME);

        if (!pom.exists()) {
            log.warn("pom.xml not found for project '{}'", projectName);
            return null;
        }

        try {
            Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(pom);
            doc.getDocumentElement().normalize();

            Node node = doc.getElementsByTagName("componentGroupName").item(0);
            if (node != null) {
                String title = node.getTextContent().trim();
                log.info("Read app title '{}' from pom.xml for project '{}'", title, projectName);
                return title;
            } else {
                log.warn("No <componentGroupName> element found in pom.xml for project '{}'", projectName);
                return null;
            }
        } catch (Exception e) {
            log.error("Failed to read app title from pom.xml for project '{}'", projectName, e);
            return null;
        }
    }

    /**
     * Generates all files for a given component request within the specified project.
     *
     * @param projectName the name of the project
     * @param request     the component request containing metadata and configuration
     */
    @Override
    public void generateComponent(String projectName, ComponentRequest request) {
        log.info("Generating component '{}' for project '{}'", request.getComponentName(), projectName);
        FileGenerationUtil.generateAllFiles(projectName, request);
        log.info("Component '{}' generation completed for project '{}'", request.getComponentName(), projectName);
    }

    /**
     * Checks if a given component name is available within a project.
     *
     * @param projectName   the name of the project
     * @param componentName the component name to check
     * @return true if the component name is available (does not exist), false otherwise
     */
    @Override
    public boolean isComponentNameAvailable(String projectName, String componentName) {

        String basePath = findComponentPathExact(projectName, componentName);

        if (basePath == null || basePath.isBlank()) {
            log.warn("Base path not found for project '{}' and component '{}'. Assuming available.", projectName, componentName);
            return true;
        }

        File componentDir = new File(basePath);

        if (componentDir.exists() && componentDir.isDirectory()) {
            log.info("Component '{}' already exists at path '{}'", componentName, basePath);
            return false;
        }

        log.info("Component '{}' is available at path '{}'", componentName, basePath);
        return true;
    }

    /**
     * Fetch all components from the local project structure, grouped by their component group.
     *
     * @param projectName the name of the project
     * @return a map where key = component group, value = list of component names
     */
    public Map<String, List<String>> getComponentsByGroup(String projectName) {

        String appId = AemUtil.getAppId(PROJECTS_DIR, projectName);

        String componentsPath = PROJECTS_DIR + "/" + projectName + CONTENT_ROOT_PATH + appId + "/" + COMPONENTS_FOLDER;

        log.info("Fetching components for project '{}' from '{}'", projectName, componentsPath);

        Map<String, List<String>> groupedComponents = new HashMap<>();

        scanComponents(new File(componentsPath), groupedComponents, APPS_PATH_PREFIX + appId + "/" + COMPONENTS_FOLDER);

        log.info("Completed fetching components. Total groups found: {}", groupedComponents.size());

        return groupedComponents;
    }


    /**
     * Recursively scans a folder to find AEM components and groups them by component group.
     *
     * @param folder            the current folder to scan
     * @param groupedComponents map of component group → list of component paths
     * @param basePath          the relative base path to prepend to each component path
     */
    private void scanComponents(File folder, Map<String, List<String>> groupedComponents, String basePath) {
        if (folder == null || !folder.exists() || !folder.isDirectory()) {
            log.warn("Folder does not exist or is not a directory: {}", folder);
            return;
        }

        File[] files = folder.listFiles();
        if (files == null) return;

        for (File file : files) {
            if (!file.isDirectory()) continue;

            String name = file.getName();

            if (EXCLUDED_FOLDERS.stream().anyMatch(ex -> name.equalsIgnoreCase(ex) || name.startsWith(ex))) {
                log.debug("Skipping excluded folder: {}", name);
                continue;
            }

            File contentXml = new File(file, ".content.xml");

            if (contentXml.exists() && isComponent(contentXml)) {
                String group = getComponentGroup(contentXml);
                if (group == null) {
                    log.debug("Skipping hidden or invalid component in folder: {}", file.getAbsolutePath());
                    continue;
                }

                groupedComponents.computeIfAbsent(group, k -> new ArrayList<>());

                String relativePath = basePath + "/" + name;

                if (!groupedComponents.get(group).contains(relativePath)) {
                    groupedComponents.get(group).add(relativePath);
                    log.info("Added component '{}' under group '{}'", relativePath, group);
                }
            }

            scanComponents(file, groupedComponents, basePath + "/" + name);
        }
    }

    /**
     * Checks whether the given .content.xml file represents an actual AEM component.
     *
     * @param contentXml The .content.xml file to check.
     * @return true if it represents a component; false otherwise.
     */
    private boolean isComponent(File contentXml) {
        if (contentXml == null || !contentXml.exists() || !contentXml.isFile()) {
            log.warn("Invalid .content.xml file: {}", contentXml);
            return false;
        }

        try {
            Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(contentXml);
            Element root = doc.getDocumentElement();

            String primaryType = root.getAttribute(JCR_PRIMARY_TYPE); // use constant if defined
            boolean isComponent = CQ_COMPONENT_PRIMARY_TYPE.equals(primaryType);

            log.debug("Checked file '{}', jcr:primaryType='{}', isComponent={}",
                    contentXml.getAbsolutePath(), primaryType, isComponent);

            return isComponent;

        } catch (Exception e) {
            log.error("Error parsing .content.xml: {}", contentXml.getAbsolutePath(), e);
            return false;
        }
    }

    /**
     * Retrieves the component group from the given .content.xml file.
     *
     * @param contentXml The .content.xml file of the component.
     * @return The component group name, or null if it is hidden, or "Others" if not defined.
     */
    private String getComponentGroup(File contentXml) {
        if (contentXml == null || !contentXml.exists() || !contentXml.isFile()) {
            log.warn("Invalid .content.xml file: {}", contentXml);
            return DEFAULT_COMPONENT_GROUP;
        }

        try {
            Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(contentXml);
            Element root = doc.getDocumentElement();

            if (root.hasAttribute(COMPONENT_GROUP)) {
                String group = root.getAttribute(COMPONENT_GROUP).trim();

                if (HIDDEN_GROUP.equalsIgnoreCase(group)) {
                    log.debug("Component '{}' is hidden; skipping group.", contentXml.getName());
                    return null;
                }

                log.debug("Component '{}' belongs to group '{}'", contentXml.getName(), group);
                return group;
            }
        } catch (Exception e) {
            log.error("Error reading component group from '{}'", contentXml.getAbsolutePath(), e);
        }

        log.debug("Component '{}' has no group defined; assigning default group '{}'", contentXml.getName(), DEFAULT_COMPONENT_GROUP);
        return DEFAULT_COMPONENT_GROUP;
    }

    /**
     * Search for a component anywhere under the project's components folder.
     * Stops at the first match since component names are unique.
     *
     * @param projectName   The AEM project name
     * @param componentName The exact name of the component to search
     * @return Full path of the component if found, otherwise null
     */
    public String findComponentPathExact(String projectName, String componentName) {
        String appId = AemUtil.getAppId(PROJECTS_DIR, projectName);

        File componentsRoot = new File(PROJECTS_DIR + "/" + projectName + CONTENT_ROOT_PATH + appId + "/" + COMPONENTS_FOLDER);

        log.debug("Searching for component '{}' in project '{}', under path '{}'", componentName, projectName, componentsRoot.getAbsolutePath());

        if (componentsRoot.exists() && componentsRoot.isDirectory()) {
            String foundPath = searchComponentRecursiveExact(componentsRoot, componentName);
            if (foundPath != null) {
                log.info("Component '{}' found at path '{}'", componentName, foundPath);
            } else {
                log.warn("Component '{}' not found in project '{}'", componentName, projectName);
            }
            return foundPath;
        } else {
            log.error("Components root folder does not exist for project '{}': {}", projectName, componentsRoot.getAbsolutePath());
            return null;
        }
    }

    /**
     * Recursively search for a component folder by exact case-sensitive name.
     * Stops at the first match since component names are unique.
     *
     * @param dir           Current directory to search
     * @param componentName The exact name of the component to match
     * @return Full path of the component if found, otherwise null
     */
    public String searchComponentRecursiveExact(File dir, String componentName) {
        if (dir == null || !dir.isDirectory()) {
            log.debug("Skipping non-directory or null path: {}", (dir != null ? dir.getPath() : "null"));
            return null;
        }

        // Exact case-sensitive match
        if (dir.getName().equals(componentName)) {
            log.info("Exact match found for component '{}' at '{}'", componentName, dir.getPath());
            return dir.getPath();
        }

        File[] subDirs = dir.listFiles(File::isDirectory);
        if (subDirs != null) {
            for (File subDir : subDirs) {
                String found = searchComponentRecursiveExact(subDir, componentName);
                if (found != null) {
                    return found;
                }
            }
        }

        log.trace("Component '{}' not found in directory '{}'", componentName, dir.getPath());
        return null;
    }

    /**
     * Fetch all component supertypes defined in the given AEM project.
     * <p>
     * Reads each component's `.content.xml`, extracts the {@code sling:resourceSuperType},
     * and builds a normalized mapping of component paths → display labels.
     * The map is returned sorted alphabetically by the label (value).
     *
     * @param projectName The name of the AEM project
     * @return A sorted map of component path → label
     */
    @Override
    public Map<String, String> fetchComponentSuperTypes(String projectName) {
        Map<String, String> superTypeMap = new LinkedHashMap<>();

        // Constants
        final String CONTENT_XML = ".content.xml";
        final String ATTR_SUPER_TYPE = "sling:resourceSuperType";

        try {
            Map<String, String> components = fetchComponentsWithGroups(projectName);
            log.info("Fetching component supertypes for project '{}', found {} components",
                    projectName, components.size());

            for (String componentName : components.keySet()) {
                String componentPath = findComponentPathExact(projectName, componentName);
                if (componentPath == null) {
                    log.warn("Component '{}' not found under project '{}'", componentName, projectName);
                    continue;
                }

                File contentXml = new File(componentPath, CONTENT_XML);
                String superType = null;

                if (contentXml.exists()) {
                    try (InputStream is = new FileInputStream(contentXml)) {
                        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
                        factory.setNamespaceAware(true);
                        DocumentBuilder builder = factory.newDocumentBuilder();
                        Document doc = builder.parse(is);

                        Element root = doc.getDocumentElement();
                        if (root.hasAttribute(ATTR_SUPER_TYPE)) {
                            superType = root.getAttribute(ATTR_SUPER_TYPE);
                            log.debug("Found supertype '{}' for component '{}'", superType, componentName);
                        }
                    } catch (Exception e) {
                        log.error("Error parsing {} for component '{}'", CONTENT_XML, componentName, e);
                    }
                } else {
                    log.warn("{} not found for component '{}'", CONTENT_XML, componentName);
                }

                String normalized = componentPath.replace(File.separatorChar, '/');
                int idx = normalized.indexOf(APPS_PATH_PREFIX);
                String componentRepoPath = (idx != -1) ? normalized.substring(idx) : componentName;

                String compLastName = componentRepoPath.substring(componentRepoPath.lastIndexOf('/') + 1);

                if (superType != null && !superType.isBlank()) {
                    String superLastName = superType.substring(superType.lastIndexOf('/') + 1);
                    String versionAwareName = extractVersionAwareName(superType);

                    if (superLastName.equals(compLastName)) {
                        putIfNotExists(superTypeMap, superType, versionAwareName);
                    } else {
                        putIfNotExists(superTypeMap, superType, versionAwareName);
                        putIfNotExists(superTypeMap, componentRepoPath, compLastName);
                    }
                } else {
                    putIfNotExists(superTypeMap, componentRepoPath, compLastName);
                }
            }
        } catch (Exception e) {
            log.error("Error fetching component supertypes for project '{}'", projectName, e);
        }

        return superTypeMap.entrySet().stream()
                .sorted(Map.Entry.comparingByValue(String.CASE_INSENSITIVE_ORDER))
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (oldVal, newVal) -> oldVal,
                        LinkedHashMap::new
                ));
    }
    /**
     * Inserts a key-value pair into the provided map only if:
     * <ul>
     *   <li>The key is not already present</li>
     *   <li>The value is not already present</li>
     * </ul>
     * This helps prevent duplicate entries both by path (key) and by label (value).
     *
     * @param map   Target map of component path → label
     * @param key   Component path (e.g., /apps/project/components/mycomp)
     * @param value Display label for the component
     */
    private void putIfNotExists(Map<String, String> map, String key, String value) {
        if (!map.containsKey(key) && !map.containsValue(value)) {
            map.put(key, value);
            log.debug("Added mapping: [{}] -> [{}]", key, value);
        } else {
            log.trace("Skipping duplicate entry for key [{}] or value [{}]", key, value);
        }
    }

    /**
     * Extracts a human-readable, version-aware label from a component supertype path.
     * <p>
     * Examples:
     * <ul>
     *   <li>{@code core/wcm/components/button/v1/button} → {@code button (v1)}</li>
     *   <li>{@code core/wcm/components/container/v2/container} → {@code container (v2)}</li>
     *   <li>{@code custom/components/teaser} → {@code teaser}</li>
     * </ul>
     * </p>
     *
     * <p>If the path does not contain a version segment (e.g., v1, v2), the method
     * simply returns the last segment of the path. If parsing fails, the original
     * {@code superTypePath} is returned unchanged.</p>
     *
     * @param superTypePath full supertype path (e.g., {@code core/wcm/components/button/v1/button})
     * @return version-aware display name
     */
    private String extractVersionAwareName(String superTypePath) {
        final String VERSION_PATTERN = "v\\d+";

        if (superTypePath == null || superTypePath.isBlank()) {
            log.warn("Received blank or null superTypePath for version-aware extraction.");
            return "";
        }

        String[] parts = superTypePath.split("/");
        if (parts.length >= 2) {
            String last = parts[parts.length - 1];
            String secondLast = parts[parts.length - 2];

            if (secondLast.matches(VERSION_PATTERN)) {
                String result = last + " (" + secondLast + ")";
                log.debug("Extracted version-aware name '{}' from superType '{}'", result, superTypePath);
                return result;
            }

            log.debug("Extracted simple name '{}' from superType '{}'", last, superTypePath);
            return last;
        }

        log.debug("Could not parse '{}', returning as-is.", superTypePath);
        return superTypePath;
    }

    /**
     * Fetches all parent dialog tabs from a given component's supertype hierarchy.
     * <p>
     * This method traverses up the inheritance chain of the specified superType
     * and collects all dialog tabs (if present). Tabs are merged into a set to
     * preserve insertion order and avoid duplicates.
     * </p>
     *
     * @param projectName the name of the AEM project
     * @param superType   the component's sling:resourceSuperType path
     * @return a result map with:
     * <ul>
     *     <li>{@code hasTabs} → boolean, true if any tabs were found</li>
     *     <li>{@code tabs} → list of tab names</li>
     * </ul>
     */
    @Override
    public Map<String, Object> getParentTabs(String projectName, String superType) {
        Map<String, Object> result = new HashMap<>();
        Set<String> tabs = new LinkedHashSet<>();

        try {

            collectTabsRecursively(projectName, superType, tabs);

            boolean hasTabs = !tabs.isEmpty();
            result.put("hasTabs", hasTabs);
            result.put("tabs", new ArrayList<>(tabs));

            log.info("Final merged tabs for superType '{}' in project '{}': {}",
                    superType, projectName, tabs);

        } catch (Exception e) {
            log.error(" Error while fetching parent tabs for superType '{}' in project '{}'",
                    superType, projectName, e);

            result.put("hasTabs", false);
            result.put("tabs", Collections.emptyList());
        }

        return result;
    }

    /**
     * Recursively collects dialog tabs from the given component and its superTypes.
     * <p>
     * The method works by:
     * <ol>
     *     <li>Checking if the component has a {@code _cq_dialog/.content.xml} and extracting its tabs.</li>
     *     <li>Looking for a {@code .content.xml} to identify its {@code sling:resourceSuperType}.</li>
     *     <li>Recursively traversing into the parent superType if found.</li>
     * </ol>
     *
     * @param projectName the AEM project name
     * @param superType   the sling:resourceSuperType path to process
     * @param tabs        the set to which tab names are added
     * @throws Exception if file parsing fails
     */
    private void collectTabsRecursively(String projectName, String superType, Set<String> tabs) throws Exception {
        if (superType == null || superType.isBlank()) {
            log.warn("Skipping empty superType for project '{}'", projectName);
            return;
        }

        boolean isCore = superType.startsWith(CORE_PREFIX);
        String basePath = System.getProperty(USER_DIR_SYS_PROP) +
                (isCore
                        ? CORE_RESOURCE_PATH_PREFIX + superType
                        : "/" + GENERATED_PROJECTS_DIR + "/" + projectName + UI_APPS_JCR_ROOT + superType);

        File dialogFile = new File(basePath, DIALOG_FILE);
        if (dialogFile.exists()) {
            List<String> currentTabs = isCore
                    ? parseCoreTabsFromDialog(dialogFile)
                    : parseProjectTabsFromDialog(dialogFile);

            if (!currentTabs.isEmpty()) {
                tabs.addAll(currentTabs);
                log.info("Tabs collected from '{}' [{}]: {}", superType, (isCore ? "core" : "project"), currentTabs);
            }
        }

        File compContentFile = new File(basePath, CONTENT_XML);
        if (!compContentFile.exists()) {
            log.debug("No .content.xml found for '{}'", superType);
            return;
        }

        String parentSuperType = readSuperType(compContentFile);
        if (parentSuperType == null || parentSuperType.isBlank()) {
            log.debug("No parent superType defined for '{}'", superType);
            return;
        }

        log.info(" '{}' extends '{}'", superType, parentSuperType);

        if (parentSuperType.startsWith(CORE_PREFIX)) {
            String corePath = System.getProperty(USER_DIR_SYS_PROP) + CORE_RESOURCE_PATH_PREFIX + parentSuperType;
            File coreDialog = new File(corePath, DIALOG_FILE);

            if (coreDialog.exists()) {
                List<String> coreTabs = parseCoreTabsFromDialog(coreDialog);
                if (!coreTabs.isEmpty()) {
                    tabs.addAll(coreTabs);
                    log.info("Core Tabs collected from '{}': {}", parentSuperType, coreTabs);
                }
            } else {
                log.warn("Core dialog not found for '{}': {}", parentSuperType, coreDialog.getAbsolutePath());
            }
        } else {
            collectTabsRecursively(projectName, parentSuperType, tabs);
        }
    }

    /**
     * Parse dialog file (.content.xml) and extract tab names for project-specific (non-core) components.
     * <p>
     * It looks for nodes with sling:resourceType = granite/ui/components/coral/foundation/tabs
     * and then iterates through their <items> children to collect tab containers.
     * </p>
     *
     * @param dialogFile the dialog .content.xml file for the component
     * @return list of tab titles detected (may be empty if no tabs found)
     * @throws Exception if XML parsing fails
     */
    private List<String> parseProjectTabsFromDialog(File dialogFile) throws Exception {
        log.info("Parsing project dialog for tabs: {}", dialogFile.getAbsolutePath());

        List<String> tabs = new ArrayList<>();

        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document doc = builder.parse(dialogFile);

        NodeList nodes = doc.getElementsByTagName("*");
        for (int i = 0; i < nodes.getLength(); i++) {
            org.w3c.dom.Node node = nodes.item(i);
            NamedNodeMap attrs = node.getAttributes();
            if (attrs == null) continue;

            // Check if this node is a <tabs> definition (granite resource type)
            org.w3c.dom.Node resType = attrs.getNamedItem(SLING_RESOURCE_TYPE);
            if (resType != null && GRANITE_TABS.equals(resType.getNodeValue())) {
                log.debug(" Found granite tabs node at index {}", i);

                NodeList itemsNodes = node.getChildNodes();
                for (int j = 0; j < itemsNodes.getLength(); j++) {
                    org.w3c.dom.Node itemsNode = itemsNodes.item(j);
                    if (!ITEMS.equals(itemsNode.getNodeName())) continue;

                    NodeList tabNodes = itemsNode.getChildNodes();
                    for (int k = 0; k < tabNodes.getLength(); k++) {
                        org.w3c.dom.Node tabNode = tabNodes.item(k);
                        if (tabNode.getNodeType() != org.w3c.dom.Node.ELEMENT_NODE) continue;

                        NamedNodeMap tabAttrs = tabNode.getAttributes();
                        if (tabAttrs == null) continue;

                        org.w3c.dom.Node tabResType = tabAttrs.getNamedItem(SLING_RESOURCE_TYPE);
                        if (tabResType != null && GRANITE_CONTAINER.equals(tabResType.getNodeValue())) {

                            String tabTitle = tabAttrs.getNamedItem(JCR_TITLE) != null
                                    ? tabAttrs.getNamedItem(JCR_TITLE).getNodeValue()
                                    : tabNode.getNodeName();

                            tabs.add(tabTitle);
                            log.info("   ➕ Project Tab detected: {}", tabTitle);
                        }
                    }
                }
            }
        }

        if (tabs.isEmpty()) {
            log.warn("No project tabs found in {}", dialogFile.getName());
        } else {
            log.info("Total project tabs collected: {}", tabs.size());
        }

        return tabs;
    }

    /**
     * Parse dialog file and extract tab names for Core components.
     * This method searches recursively until the first <tabs> node is found.
     *
     * @param dialogFile the .content.xml dialog file
     * @return list of tab titles found in the Core component
     * @throws Exception if parsing fails
     */
    private List<String> parseCoreTabsFromDialog(File dialogFile) throws Exception {
        List<String> tabs = new ArrayList<>();
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document doc = builder.parse(dialogFile);

        org.w3c.dom.Node root = doc.getDocumentElement();
        log.debug("Starting recursive parsing for Core tabs in file: {}", dialogFile.getAbsolutePath());

        parseTabsRecursive(root, tabs);

        log.info("Core tabs parsed from {}: {}", dialogFile.getName(), tabs);
        return tabs;
    }

    /**
     * Recursively parses XML nodes to extract tab names.
     * Supports both Core and project components.
     * <p>
     * Stops at <tabs> nodes or nodes with sling:resourceType = coral/foundation/tabs,
     * and extracts <container> nodes with jcr:title as tab titles.
     *
     * @param node current XML node being inspected
     * @param tabs collection of tab titles found so far
     */
    private void parseTabsRecursive(org.w3c.dom.Node node, List<String> tabs) {
        if (node.getNodeType() != org.w3c.dom.Node.ELEMENT_NODE) {
            return;
        }

        NamedNodeMap attrs = node.getAttributes();

        if (TYPE_TABS.equals(node.getNodeName()) ||
                (attrs != null && attrs.getNamedItem(SLING_RESOURCE_TYPE) != null &&
                        GRANITE_TABS.equals(attrs.getNamedItem(SLING_RESOURCE_TYPE).getNodeValue()))) {

            log.debug(" Found <tabs> node at: {}", node.getNodeName());

            NodeList itemsNodes = node.getChildNodes();
            for (int i = 0; i < itemsNodes.getLength(); i++) {
                org.w3c.dom.Node itemsNode = itemsNodes.item(i);
                if (!ITEMS.equals(itemsNode.getNodeName())) continue;

                NodeList tabNodes = itemsNode.getChildNodes();
                for (int j = 0; j < tabNodes.getLength(); j++) {
                    org.w3c.dom.Node tabNode = tabNodes.item(j);
                    if (tabNode.getNodeType() != org.w3c.dom.Node.ELEMENT_NODE) continue;

                    NamedNodeMap tabAttrs = tabNode.getAttributes();
                    if (tabAttrs == null) continue;

                    org.w3c.dom.Node resTypeAttr = tabAttrs.getNamedItem(SLING_RESOURCE_TYPE);
                    if (resTypeAttr != null &&
                            GRANITE_CONTAINER.equals(resTypeAttr.getNodeValue())) {

                        org.w3c.dom.Node titleAttr = tabAttrs.getNamedItem(JCR_TITLE);
                        String tabTitle = (titleAttr != null) ? titleAttr.getNodeValue() : tabNode.getNodeName();
                        tabs.add(tabTitle);
                        log.info("   ➕ Core Tab detected: {}", tabTitle);
                    }

                    parseTabsRecursive(tabNode, tabs);
                }
            }
        } else {

            NodeList children = node.getChildNodes();
            for (int i = 0; i < children.getLength(); i++) {
                parseTabsRecursive(children.item(i), tabs);
            }
        }
    }



    /**
     * Reads sling:resourceSuperType from .content.xml.
     * This method parses the given component's .content.xml file
     * and looks for the sling:resourceSuperType attribute.
     *
     * @param compContentFile the .content.xml file of the component
     * @return the value of sling:resourceSuperType if found, otherwise null
     */
    private String readSuperType(File compContentFile) throws Exception {
        log.info(" Reading superType from file: {}", compContentFile.getAbsolutePath());

        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document doc = builder.parse(compContentFile);

        NodeList rootNodes = doc.getElementsByTagName("*");
        for (int i = 0; i < rootNodes.getLength(); i++) {
            org.w3c.dom.Node node = rootNodes.item(i);
            NamedNodeMap attrs = node.getAttributes();

            if (attrs != null) {
                for (int j = 0; j < attrs.getLength(); j++) {
                    org.w3c.dom.Node attr = attrs.item(j);
                    String name = attr.getNodeName();
                    String value = attr.getNodeValue();

                    log.debug("Attribute found: {} = {}", name, value);

                    if (SLING_RESOURCE_SUPER_TYPE.equals(name) || name.endsWith(SUFFIX_RESOURCE_SUPER_TYPE)) {
                        log.info(" Found superType: {}", value);
                        return value;
                    }
                }
            }
        }

        log.warn("No sling:resourceSuperType found in {}", compContentFile.getName());
        return null;
    }

}
