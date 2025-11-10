package com.aem.builder.service.impl;

import com.aem.builder.model.DTO.ComponentField;
import com.aem.builder.model.DTO.ComponentRequest;
import com.aem.builder.model.DTO.OptionItem;
import com.aem.builder.model.Enum.FieldType;
import com.aem.builder.service.ComponentService;
import com.aem.builder.util.AemUtil;
import com.aem.builder.util.FileGenerationUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Service;
import org.w3c.dom.*;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
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
        File componentsDir = new File(PROJECTS_DIR, projectName + "/" + CONTENT_ROOT_PATH + "/" + appId + "/" + COMPONENTS_FOLDER);

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
                log.info("[getAllComponents] Discovered component resource: {}", fileName);
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
        log.info("[getDistinctComponents] Distinct components list: {}", distinct);

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
                log.info("[loadComponent] Reading component metadata from '{}'", contentXml.getAbsolutePath());
                String content = FileGenerationUtil.readFile(contentXml);

                group = extractProperty(content, COMPONENT_GROUP).trim();
                superType = extractProperty(content, SLING_RESOURCE_SUPER_TYPE).trim();

                log.info("[loadComponent] Parsed component metadata: group='{}', superType='{}'", group, superType);
            } else {
                log.info("[loadComponent] .content.xml not found for component '{}'", componentName);
            }

            File dialogXml = new File(basePath + "/" + CQ_DIALOG + "/" + CONTENT_XML);
            if (dialogXml.exists()) {
                log.info("[loadComponent] Parsing dialog XML at '{}'", dialogXml.getAbsolutePath());

                DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
                factory.setNamespaceAware(false); // disabling namespaces for easier parsing
                DocumentBuilder builder = factory.newDocumentBuilder();
                Document doc = builder.parse(dialogXml);

                collectFields(doc.getDocumentElement(), fields);
                log.info("[loadComponent] Collected {} field(s) for component '{}'", fields.size(), componentName);
            } else {
                log.info("[loadComponent] Dialog XML not found for component '{}'", componentName);
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

//  generated-projects/demo/ui.apps/src/main/content/jcr_root/apps/appid
        String compPath = PROJECTS_DIR + "/" + projectName + "/" + CONTENT_ROOT_PATH + "/" + appId + "/"+ COMPONENTS_FOLDER + "/"+ request.getComponentName();

        log.info("[updateComponent] Updating component '{}' in project '{}'", request.getComponentName(), projectName);
        log.info("[updateComponent] Resolved component path for update: {}", compPath);

        /*try {
            FileUtils.deleteDirectory(new File(compPath));
            log.info("[updateComponent] Deleted existing component directory: {}", compPath);
        } catch (IOException e) {
            log.info("[updateComponent] Could not clean component folder before update for '{}'", request.getComponentName(), e);
        }*/

        File componentFolder = new File(compPath);
        log.info("Checking component path: {}", componentFolder);

       // FileGenerationUtil.generateAllFiles(projectName, request);
        // log.info("[updateComponent] Regenerated component '{}' in project '{}'", request.getComponentName(), projectName);
        if (!componentFolder.exists()) {
            // Component does not exist → generate new
            log.info("generate the component :");
            FileGenerationUtil.generateAllFiles(projectName, request);
        } else {
            // Component exists → update all files
            log.info("update the component :");
            FileGenerationUtil.updateAllFiles(projectName, request);
        }
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
            log.info("[deleteComponent] Component '{}' not found in project '{}'", componentName, projectName);
            return;
        }
        log.info("[deleteComponent] Component folder resolved at '{}'", compPath);

        Set<String> slingModels = new HashSet<>();
        try {
            slingModels = collectSlingModelsFromHTLFolder(new File(compPath));
            log.info("[deleteComponent] Sling Models found: {}", slingModels);
        } catch (IOException e) {
            log.error("[deleteComponent] Failed to read HTL files for component '{}'", componentName, e);
        }

        ComponentRequest req = loadComponent(projectName, componentName);
        Set<String> multifieldNames = req.getFields().stream()
                .filter(f -> TYPE_MULTIFIELD.equals(f.getFieldType()))
                .map(ComponentField::getFieldName)
                .collect(Collectors.toSet());
        log.info("[deleteComponent] Multifield names collected: {}", multifieldNames);

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
        log.info("[deleteComponent] Java classes marked for deletion: {}", javaClassesToDelete);

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
            log.info("[collectSlingModelsFromHTLFolder] Provided path '{}' is not a directory", dir.getAbsolutePath());
            return slingModels;
        }

        File[] files = dir.listFiles();
        if (files == null) return slingModels;

        for (File file : files) {
            if (file.isDirectory()) {
                log.info("[collectSlingModelsFromHTLFolder] Entering directory '{}'", file.getAbsolutePath());
                slingModels.addAll(collectSlingModelsFromHTLFolder(file));
            } else if (file.isFile() && file.getName().endsWith(HTL_FILE_EXTENSION)) {
                log.info("[collectSlingModelsFromHTLFolder] Processing HTL file '{}'", file.getAbsolutePath());
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
        log.info("[extractSlingModelsFromHTL] Extracting Sling Models from HTL file '{}'", htlFile.getAbsolutePath());

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
                        log.info("[extractSlingModelsFromHTL] Found Sling Model reference: '{}'", ref);
                    }
                }
            }
        }

        log.info("[extractSlingModelsFromHTL] Extracted Sling Models from '{}': {}", htlFile.getName(), classes);
        return classes;
    }

    /**
     * Recursively collects a Java class and its child classes for deletion.
     * <p>
     * The method processes a given Java class file to detect:
     * <ul>
     *     <li>@ChildResource annotated fields referencing other classes</li>
     *     <li>Multifield child classes based on provided field names</li>
     * </ul>
     * It adds all discovered class file names to {@code javaClassesToDelete} and tracks processed
     * classes in {@code processedClasses} to avoid infinite recursion.
     * </p>
     *
     * @param javaRoot            the root directory of Java source files
     * @param className           the simple name of the class to process
     * @param javaClassesToDelete the set of Java class file names to delete
     * @param processedClasses    the set of classes already processed to prevent recursion
     * @param multifieldNames     the set of multifield names to detect child classes
     * @throws IOException if there is an error reading Java class files
     */
    private void collectClassAndChildren(Path javaRoot, String className,
                                         Set<String> javaClassesToDelete,
                                         Set<String> processedClasses,
                                         Set<String> multifieldNames) throws IOException {

        if (processedClasses.contains(className)) {
            log.info("[collectClassAndChildren] Class '{}' already processed, skipping recursion", className);
            return;
        }
        processedClasses.add(className);

        // Locate the Java class file
        Path classPath = findJavaClassRecursive(javaRoot, className + JAVA_EXTENSION);
        if (classPath == null) {
            log.info("[collectClassAndChildren] Java class '{}' not found under '{}'", className, javaRoot);
            return;
        }
        log.info("[collectClassAndChildren] Found Java class '{}' at '{}'", className, classPath);

        javaClassesToDelete.add(classPath.getFileName().toString());

        // Read all lines from the class
        List<String> lines = Files.readAllLines(classPath);

        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i).trim();

            // 1. Detect @ChildResource fields
            if (line.contains(CHILD_RESOURCE_ANNOTATION)) {
                for (int j = i + 1; j < Math.min(i + 4, lines.size()); j++) {
                    String fieldLine = lines.get(j).trim();
                    Matcher m = Pattern.compile(CHILD_CLASS_FIELD_PATTERN).matcher(fieldLine);
                    if (m.find()) {
                        String childClass = m.group(1) != null ? m.group(1) :
                                m.group(2) != null ? m.group(2) : m.group(3);
                        if (childClass != null && !childClass.equals(className)) {
                            log.info("[collectClassAndChildren] Found @ChildResource child class '{}' in '{}'", childClass, className);
                            collectClassAndChildren(javaRoot, childClass, javaClassesToDelete, processedClasses, multifieldNames);
                        }
                    }
                }
            }

            // 2. Detect multifield child classes
            for (String mfName : multifieldNames) {
                if (line.matches(".*\\b([A-Z]\\w+)\\s+" + mfName + ";.*")) {
                    String childClassName = line.replaceAll(".*\\b([A-Z]\\w+)\\s+" + mfName + ";.*", "$1");
                    log.info("[collectClassAndChildren] Found child class '{}' for multifield '{}' in '{}'", childClassName, mfName, className);
                    collectClassAndChildren(javaRoot, childClassName, javaClassesToDelete, processedClasses, multifieldNames);
                }
            }
        }
    }

    /**
     * Recursively searches for a Java class file within the given root directory.
     * <p>
     * This method performs a depth-first walk of the directory tree starting from
     * {@code javaRoot} to locate a Java source file that matches the specified
     * {@code className}. If found, it returns the {@link Path} to the matching file;
     * otherwise, it returns {@code null}.
     * </p>
     *
     * @param javaRoot  the root directory to search for the Java class file
     * @param className the name of the Java class file to locate (e.g., "MyModel.java")
     * @return the {@link Path} of the found Java class file, or {@code null} if not found
     * @throws IOException if an I/O error occurs while walking the file tree
     */
    private Path findJavaClassRecursive(Path javaRoot, String className) throws IOException {
        log.info("[findJavaClassRecursive] Starting search for Java class '{}' under '{}'", className, javaRoot);

        try (Stream<Path> paths = Files.walk(javaRoot)) {
            Path found = paths
                    .filter(p -> p.getFileName().toString().equals(className))
                    .findFirst()
                    .orElse(null);

            if (found != null) {
                log.info("[findJavaClassRecursive] Java class '{}' found at '{}'", className, found);
            } else {
                log.info("[findJavaClassRecursive] Java class '{}' not found under '{}'", className, javaRoot);
            }

            log.info("[findJavaClassRecursive] Search completed for class '{}'", className);
            return found;
        }
    }

    /**
     * Retrieves the HTML (HTL) content of a specified AEM component within a given project.
     * <p>
     * This method locates the exact component path using the project and component names,
     * constructs the HTL file path, and reads its content. If the component or its HTL file
     * cannot be found or read, it returns an empty string.
     * </p>
     *
     * @param projectName   the name of the AEM project where the component resides
     * @param componentName the name of the AEM component whose HTML content is to be retrieved
     * @return the HTML (HTL) content of the component as a {@link String}, or an empty string
     * if the component is not found or the file cannot be read
     */
    @Override
    public String getComponentHtml(String projectName, String componentName) {

        log.info("[getComponentHtml] Fetching HTML for component '{}' in project '{}'", componentName, projectName);

        String componentPathExact = findComponentPathExact(projectName, componentName);
        if (componentPathExact == null) {
            log.info("[getComponentHtml] Component '{}' not found in project '{}'", componentName, projectName);
            return "";
        }

        Path htmlPath = Paths.get(componentPathExact, componentName + HTL_FILE_EXTENSION);

        try {
            String htmlContent = Files.readString(htmlPath);
            log.info("[getComponentHtml] Successfully read HTML content for component '{}'", componentName);
            return htmlContent;
        } catch (IOException e) {
            log.error("[getComponentHtml] Failed to read HTML for component '{}' at '{}'", componentName, htmlPath, e);
            return "";
        }
    }

    /**
     * Retrieves the complete Java (Sling Model) code for a specified AEM component.
     * <p>
     * This method first reads the HTL (HTML) of the given component to locate the
     * Sling Model binding using the {@code data-sly-use} pattern. Once the primary
     * model class is identified, it recursively resolves all referenced Java classes
     * to collect their source code. The final result includes the main model class
     * and all dependent classes, each separated by a visual delimiter.
     * </p>
     *
     * <p><b>Behavior:</b></p>
     * <ul>
     *     <li>If no HTL file is found for the component, a comment message is returned.</li>
     *     <li>If no Sling Model binding is found in the HTL, a comment message is returned.</li>
     *     <li>If any Java file cannot be read, it is skipped, and the process continues.</li>
     * </ul>
     *
     * @param projectName   the name of the AEM project containing the component
     * @param componentName the name of the AEM component whose Sling Model Java code is to be retrieved
     * @return a {@link String} containing the Java code of the Sling Model and all dependent classes,
     * or a comment message if the HTL or Sling Model binding cannot be resolved
     */
    @Override
    public String getComponentJava(String projectName, String componentName) {
        log.info("[getComponentJava] Fetching Sling Model Java code for component '{}' in project '{}'", componentName, projectName);

        try {
            // Step 1: Read the HTL file content for the component
            String htlContent = getComponentHtml(projectName, componentName);

            if (htlContent == null || htlContent.isEmpty()) {
                log.info("[getComponentJava] No HTL found for component '{}'", componentName);
                return "// No HTL found for component: " + componentName;
            }

            // Step 2: Extract Sling Model binding from HTL
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
                log.info("[getComponentJava] No Sling Model binding found in HTL for component '{}'", componentName);
                return "// No Sling Model binding found in HTL for: " + componentName;
            }

            // Step 3: Recursively resolve and read all referenced Java classes
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

                // Find and add referenced classes from the current Java file
                Matcher refMatcher = Pattern.compile(JAVA_CLASS_REF_PATTERN).matcher(code);
                while (refMatcher.find()) {
                    String refClass = refMatcher.group(1);
                    if (!refClass.equals(simpleName)) {
                        stack.push(packageName + "." + refClass);
                    }
                }
            }

            log.info("[getComponentJava] Successfully fetched Java code for component '{}'", componentName);
            return result.toString();

        } catch (Exception e) {
            log.error("[getComponentJava] Failed while resolving Sling Model for component '{}'", componentName, e);
            return "// Error while resolving Sling Model: " + e.getMessage();
        }
    }

    /**
     * Determines the dialog field type corresponding to a given AEM resource type.
     * <p>
     * This method iterates through all {@link FieldType} enum values to find a match for the
     * provided {@code resourceType}. If a match is found, the associated field type is returned.
     * If no match exists or the input is null/empty, an empty string is returned.
     * </p>
     *
     * @param resourceType the AEM resource type (e.g., "granite/ui/components/foundation/form/textfield")
     * @return the matching field type as a {@link String}, or an empty string if no match is found
     */
    private String getFieldTypeFromResource(String resourceType) {
        log.info("[getFieldTypeFromResource] Resolving field type for resourceType '{}'", resourceType);

        if (resourceType == null || resourceType.isEmpty()) {
            log.info("[getFieldTypeFromResource] Resource type is null or empty, returning empty string");
            return "";
        }

        String fieldType = "";
        for (FieldType ft : FieldType.values()) {
            // Skip MULTISELECT; we only want SELECT by default
            if (ft == FieldType.MULTISELECT) continue;
            if (ft.getResourceType().equals(resourceType)) {
                log.info("[getFieldTypeFromResource] Matched resourceType '{}' to fieldType '{}'", resourceType, ft.getType());
                return ft.getType(); // Return immediately on first match
            }
        }

        if (fieldType.isEmpty()) {
            log.info("[getFieldTypeFromResource] No matching fieldType found for resourceType '{}'", resourceType);
        } else {
            log.info("[getFieldTypeFromResource] Successfully resolved fieldType '{}' for resourceType '{}'", fieldType, resourceType);
        }
        log.info("fieldType......{}",fieldType);
        return fieldType;
    }

    /**
     * Determines the dialog field type for the given XML {@link Element}.
     * <p>
     * This method inspects the element's {@code sling:resourceType} and other
     * attributes to map it to a specific field type used in AEM component dialogs.
     * The detection order is:
     * <ol>
     *     <li>Checks {@code sling:resourceType} for a direct match using
     *         {@link #getFieldTypeFromResource(String)}</li>
     *     <li>Overrides the type if the element represents a multifield</li>
     *     <li>Overrides the type if the element represents a multiselect</li>
     *     <li>Detects file or image upload types based on the node name</li>
     * </ol>
     * </p>
     *
     * @param elem the XML {@link Element} representing the dialog field
     * @return the resolved field type as a {@link String}; may be an empty string if no match is found
     */
    private String determineFieldType(Element elem) {
        log.info("[determineFieldType] Determining field type for element '{}'", elem.getNodeName());

        // 1. Start with the resourceType-based mapping
        String resourceType = elem.getAttribute(SLING_RESOURCE_TYPE);
        String type = getFieldTypeFromResource(resourceType);

        log.info("[determineFieldType] Initial type '{}' detected from resourceType '{}'", type, resourceType);

        // 2. Override for specific dialog components
        if (GRANITE_MULTIFIELD.equals(resourceType)) {
            type = TYPE_MULTIFIELD;
            log.info("[determineFieldType] Detected multifield for element '{}'", elem.getNodeName());
        } else if (GRANITE_SELECT.equals(resourceType)
                &&"true".equalsIgnoreCase(elem.getAttribute("multiple"))|| "{Boolean}true".equalsIgnoreCase(elem.getAttribute("multiple"))) {
            type = TYPE_MULTISELECT;
            log.info("[determineFieldType] Detected multiselect for element '{}'", elem.getNodeName());
        } else if (CQ_FILEUPLOAD.equals(resourceType)) {
            String node = elem.getNodeName().toLowerCase();
            if (node.contains("file")) {
                type = FILEUPLOAD;
            } else if (node.contains(IMAGE)) {
                type = IMAGE;
            }
            log.info("[determineFieldType] Detected '{}' upload type for element '{}'", type, elem.getNodeName());
        }

        // 3. Final result
        log.info("[determineFieldType] Final field type resolved for element '{}': '{}'", elem.getNodeName(), type);
        return type;
    }

    /**
     * Parses an XML {@link Element} representing a dialog field into a {@link ComponentField} object.
     * <p>
     * This method determines the field's type, name, label, and nested fields or options based
     * on the element's attributes and child elements. Special handling is included for:
     * <ul>
     *     <li>Multifields: recursively collects nested fields</li>
     *     <li>Select, multiselect, and radiogroup fields: extracts option items</li>
     *     <li>Tabs: collects child fields and sets tab name and label</li>
     * </ul>
     * </p>
     *
     * @param elem the XML {@link Element} representing the field in the component dialog
     * @return a {@link List} containing one or more {@link ComponentField} objects parsed from the element
     */
    private List<ComponentField> parseField(Element elem) {
        log.info("[parseField] Parsing field element '{}'", elem.getNodeName());

        List<ComponentField> result = new ArrayList<>();

        String fieldLabel = elem.getAttribute(ATTR_FIELD_LABEL);
        String nameAttr = elem.getAttribute(KEY_NAME);
        String fileRefAttr = elem.getAttribute(ATTR_FILE_REFERENCE);
        String resourceType = elem.getAttribute(SLING_RESOURCE_TYPE);
        String fieldType = getFieldTypeFromResource(resourceType);

        String fieldName = null;

        log.info("[parseField] Initial resourceType='{}', fieldType='{}'", resourceType, fieldType);

        // Resolve multifield name if necessary
        if (TYPE_MULTIFIELD.equals(fieldType) && (nameAttr == null || nameAttr.isBlank())) {
            NodeList fieldNodes = elem.getElementsByTagName(ATTR_FIELD);
            if (fieldNodes.getLength() > 0) {
                Element fieldElem = (Element) fieldNodes.item(0);
                String nestedFieldName = fieldElem.getAttribute(KEY_NAME);
                if (nestedFieldName != null && !nestedFieldName.isBlank()) {
                    fieldName = nestedFieldName.startsWith("./") ? nestedFieldName.substring(2) : nestedFieldName;
                }
            }
            log.info("[parseField] Resolved multifield name: '{}'", fieldName);
        }

        // Fallback to main field name or file reference
        if (fieldName == null && nameAttr != null && !nameAttr.isBlank()) {
            if (FILEUPLOAD.equals(fieldType) && fileRefAttr != null && !fileRefAttr.isBlank()) {
                fieldName = fileRefAttr.startsWith("./") ? fileRefAttr.substring(2) : fileRefAttr;
            } else {
                fieldName = nameAttr.startsWith("./") ? nameAttr.substring(2) : nameAttr;
            }
        }

        log.info("[parseField] Field parsed: label='{}', name='{}', type='{}', resourceType='{}'",
                fieldLabel, fieldName, fieldType, resourceType);

        List<OptionItem> options = null;
        List<ComponentField> nested = null;

        // Handle nested/multifield elements
        if (TYPE_MULTIFIELD.equals(fieldType)) {
            nested = new ArrayList<>();
            NodeList fieldNodes = elem.getElementsByTagName(ITEMS);
            if (fieldNodes.getLength() > 0) {
                Element itemsElem = (Element) fieldNodes.item(0);
                collectFields(itemsElem, nested);
            }
        }
        // Handle select/multiselect/radiogroup options
        else if (SELECT.equals(fieldType) || TYPE_MULTISELECT.equals(fieldType) || RADIOGROUP.equals(fieldType)) {
            if (SELECT.equals(fieldType)) {
                String multipleAttr = elem.getAttribute(ATTR_MULTIPLE);
                if (VALUE_TRUE.equalsIgnoreCase(multipleAttr)||VALUE_BOOLEAN_TRUE.equalsIgnoreCase(multipleAttr)) {
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
        // Handle tab fields
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
     * Recursively collects {@link ComponentField} objects from the given parent XML {@link Element}.
     * <p>
     * This method traverses all child nodes of the parent element and:
     * <ul>
     *     <li>Determines the field type of each child using {@link #determineFieldType(Element)}</li>
     *     <li>Parses individual fields with {@link #parseField(Element)}</li>
     *     <li>Handles special cases such as containers and tabs</li>
     *     <li>Recursively collects nested fields for multifields or unknown types</li>
     * </ul>
     * The resulting {@link ComponentField} objects are added to the provided {@code fields} list.
     * </p>
     *
     * @param parent the parent XML {@link Element} to parse for fields
     * @param fields the {@link List} where parsed {@link ComponentField} objects will be collected
     */
    private void collectFields(Element parent, List<ComponentField> fields) {
        NodeList children = parent.getChildNodes();
        log.info("[collectFields] Collecting fields from parent node '{}', child count={}", parent.getNodeName(), children.getLength());

        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (!(node instanceof Element elem)) {
                continue;
            }

            String resourceType = elem.getAttribute(SLING_RESOURCE_TYPE);
            String type = determineFieldType(elem);

            // Handle container elements
            if (GRANITE_CONTAINER.equals(resourceType)) {
                String parentResourceType = getParentResourceType(elem);

                log.info("[collectFields] Checking container: nodeName='{}', resourceType='{}', parentResourceType='{}'",
                        elem.getNodeName(), resourceType, parentResourceType);

                if (!GRANITE_TABS.equals(parentResourceType)) {
                    log.info("[collectFields] Skipping container '{}' as a field, but parsing its children", elem.getNodeName());
                    collectFields(elem, fields);
                    continue;
                }
            }

            // Parse valid fields
            if (!type.isEmpty()) {
                List<ComponentField> parsed = parseField(elem);
                if (parsed != null && !parsed.isEmpty()) {
                    log.info("[collectFields] Adding {} parsed field(s) from node '{}'", parsed.size(), elem.getNodeName());
                    fields.addAll(parsed);
                } else {
                    log.info("[collectFields] No valid fields parsed from node '{}'", elem.getNodeName());
                }
            } else {
                // Recursively collect children if type is unknown or empty
                log.info("[collectFields] Recursing into children of node '{}' with unknown or empty type", elem.getNodeName());
                collectFields(elem, fields);
            }
        }
    }

    /**
     * Retrieves the {@code sling:resourceType} of the nearest parent element in the XML hierarchy.
     * <p>
     * This method traverses the ancestor nodes of the given element and returns the first
     * {@code sling:resourceType} it finds. If no parent with a resource type exists, it returns
     * an empty string.
     * </p>
     *
     * @param elem the XML {@link Element} whose parent resource type is to be determined
     * @return the {@code sling:resourceType} of the nearest parent element, or an empty string if none is found
     */
    private String getParentResourceType(Element elem) {
        log.info("[getParentResourceType] Searching for parent resourceType of node '{}'", elem.getNodeName());

        Node parent = elem.getParentNode();
        while (parent instanceof Element parentElem) {
            if (parentElem.hasAttribute(SLING_RESOURCE_TYPE)) {
                String resourceType = parentElem.getAttribute(SLING_RESOURCE_TYPE);
                log.info("[getParentResourceType] Found parent resourceType '{}' at node '{}'", resourceType, parentElem.getNodeName());
                return resourceType;
            }
            parent = parent.getParentNode();
        }

        log.info("[getParentResourceType] No parent resourceType found for node '{}'", elem.getNodeName());
        return "";
    }

    /**
     * Retrieves a list of component names for a given AEM project.
     * <p>
     * This method fetches all components along with their groups using
     * {@link #fetchComponentsWithGroups(String)} and returns only the component names.
     * If no components are found, it returns an empty list.
     * </p>
     *
     * @param projectName the name of the AEM project
     * @return a {@link List} of component names, or an empty list if no components exist
     */
    @Override
    public List<String> getProjectComponentsMap(String projectName) {
        log.info("[getProjectComponentsMap] Fetching components for project '{}'", projectName);

        Map<String, String> components = fetchComponentsWithGroups(projectName);
        if (components == null || components.isEmpty()) {
            log.info("[getProjectComponentsMap] No components found for project '{}'", projectName);
            return Collections.emptyList();
        }

        List<String> componentNames = new ArrayList<>(components.keySet());
        log.info("[getProjectComponentsMap] Found {} component(s) for project '{}'", componentNames.size(), projectName);
        return componentNames;
    }

    /**
     * Adds the specified components to an existing AEM project.
     * <p>
     * This method calculates the target content folder path based on the project name
     * and application ID, then copies the selected components into the project structure.
     * All key operations are logged for traceability.
     * </p>
     *
     * @param projectName        the name of the existing AEM project
     * @param selectedComponents a {@link List} of component names to be added to the project
     */
    @Override
    public void addComponentsToExistingProject(String projectName, List<String> selectedComponents) {
        log.info("[addComponentsToExistingProject] Adding {} component(s) to existing project '{}'",
                selectedComponents.size(), projectName);

        try {
            // Determine base directory and app ID
            String baseDir = System.getProperty(USER_DIR_SYS_PROP) + "/" + PROJECTS_DIR + "/";
            String appId = AemUtil.getAppId(PROJECTS_DIR, projectName);

            String contentFolderPath = baseDir + projectName + "/" + CONTENT_ROOT_PATH + "/" +
                    appId + "/" + COMPONENTS_FOLDER;

            log.info("[addComponentsToExistingProject] Copying components {} to '{}'",
                    selectedComponents, contentFolderPath);

            // Copy selected components to the project
            copySelectedComponents(selectedComponents, contentFolderPath, projectName);

            log.info("[addComponentsToExistingProject] Successfully added components to project '{}'", projectName);

        } catch (Exception e) {
            log.error("[addComponentsToExistingProject] Error while adding components to project '{}'", projectName, e);
        }
    }

    /**
     * Copies the selected AEM components into the target project directory, including
     * updating content and HTL files, and copying associated Sling Models with dependencies.
     * <p>
     * Steps performed for each component:
     * <ol>
     *     <li>Check if the source component exists.</li>
     *     <li>Delete the destination folder if it already exists.</li>
     *     <li>Copy the component folder to the target path.</li>
     *     <li>Update the {@code sling:resourceType} in the component's {@code .content.xml}.</li>
     *     <li>Update HTL file's {@code data-sly-use} reference to the fully qualified Sling Model class.</li>
     *     <li>Copy the corresponding Sling Model and its dependencies.</li>
     * </ol>
     * </p>
     *
     * @param selectedComponents a {@link List} of component names to copy
     * @param targetPath         the target directory where components will be copied
     * @param projectName        the name of the project for logging and resource path updates
     */
    @Override
    public void copySelectedComponents(List<String> selectedComponents, String targetPath, String projectName) {
        if (selectedComponents == null || selectedComponents.isEmpty()) {
            log.info("[copySelectedComponents] No components selected to copy for project '{}'", projectName);
            return;
        }

        log.info("[copySelectedComponents] Starting to copy {} component(s) to project '{}'",
                selectedComponents.size(), projectName);

        String slingModelsSourcePath = System.getProperty(USER_DIR_SYS_PROP) + "/" + SLING_MODELS_SOURCE;
        Path javaSourceRoot = Paths.get(PROJECTS_DIR, projectName, JAVA_SRC_PATH);

        Path modelPath = findModelBasePath(javaSourceRoot);
        log.info("[copySelectedComponents] Model base path found: {}", modelPath);

        String modelBasePath = modelPath.toString();
        String packageName = javaSourceRoot.relativize(modelPath).toString().replace(File.separatorChar, '.');

        Set<String> copiedModels = new HashSet<>();

        for (String component : selectedComponents) {
            try {
                File source = new File(AEM_COMPONENTS_SOURCE + "/" + component);
                File destination = new File(targetPath + "/" + component);

                if (!source.exists()) {
                    log.info("[copySelectedComponents] Source component not found: {}", source.getAbsolutePath());
                    continue;
                }

                if (destination.exists()) {
                    FileUtils.deleteDirectory(destination);
                    log.info("[copySelectedComponents] Deleted existing component folder: {}", destination.getAbsolutePath());
                }

                FileUtils.copyDirectory(source, destination);
                log.info("[copySelectedComponents] Copied component '{}' to '{}'", component, destination.getAbsolutePath());

                // Update sling:resourceType in content.xml
                File contentXml = new File(destination, CONTENT_XML);
                if (contentXml.exists()) {
                    String content = FileUtils.readFileToString(contentXml, UTF_8);
                    content = content.replaceAll(SLING_RESOURCE_TYPE_PATTERN,
                            SLING_RESOURCE_TYPE_ATTR + projectName + "/" + COMPONENTS_FOLDER + "/" +
                                    component.toLowerCase() + "\"");
                    FileUtils.writeStringToFile(contentXml, content, UTF_8);
                    log.info("[copySelectedComponents] Updated sling:resourceType in '{}'", contentXml.getAbsolutePath());
                }

                // Update HTL model reference
                File html = new File(destination, component + HTL_FILE_EXTENSION);
                File parentModel = findMatchingModelFile(slingModelsSourcePath, component);
                log.info("[copySelectedComponents] Parent model for '{}': {}", component, parentModel);
                if (html.exists() && parentModel != null) {
                    String htmlContent = FileUtils.readFileToString(html, UTF_8);
                    String fqcn = extractFullyQualifiedClassName(parentModel, packageName);
                    if (fqcn != null) {
                        htmlContent = htmlContent.replaceAll(DATA_SLY_USE_MODEL_PATTERN,
                                DATA_SLY_USE_MODEL + fqcn + "\"");
                        FileUtils.writeStringToFile(html, htmlContent, UTF_8);
                        log.info("[copySelectedComponents] Updated HTL model reference in '{}'", html.getAbsolutePath());
                    }
                }
                // Update componentGroup in .content.xml
                //File contentXml = new File(destination, CONTENT_XML);
                String capitalizedProject = projectName.substring(0, 1).toUpperCase() + projectName.substring(1);

                if (contentXml.exists()) {
                    String content = FileUtils.readFileToString(contentXml, UTF_8);

                    // Replace componentGroup="anything"
                    content = content.replaceAll("componentGroup=\"[^\"]*\"",
                            "componentGroup=\"" + capitalizedProject + " - Content\"");
                    FileUtils.writeStringToFile(contentXml, content, UTF_8);
                    log.info("[copySelectedComponents] Updated componentGroup in '{}'", contentXml.getAbsolutePath());
                }


                // Copy model and dependencies
                if (parentModel != null && parentModel.exists()) {
                    copyModelAndDependencies(parentModel, slingModelsSourcePath, modelBasePath, packageName, copiedModels);
                    log.info("[copySelectedComponents] Copied Sling Model and dependencies for component '{}'", component);
                } else {
                    log.info("[copySelectedComponents] No matching Sling Model found for component '{}'", component);
                }

            } catch (IOException e) {
                log.error("[copySelectedComponents] Failed to process component '{}'", component, e);
            }
        }

        log.info("[copySelectedComponents] Finished copying selected components for project '{}'", projectName);
    }

    /**
     * Searches recursively for the 'models' directory under the given Java source root.
     * <p>
     * This method traverses the directory tree starting from {@code javaSourceRoot} and
     * returns the first directory named {@code models}. If no such directory is found,
     * an exception is thrown.
     * </p>
     *
     * @param javaSourceRoot the root path of Java source files to search
     * @return the {@link Path} to the 'models' directory
     * @throws RuntimeException if an I/O error occurs or the 'models' directory is not found
     */
    private static Path findModelBasePath(Path javaSourceRoot) {
        log.info("[findModelBasePath] Searching for 'models' directory under Java source root: {}", javaSourceRoot);

        try (Stream<Path> paths = Files.walk(javaSourceRoot)) {

            Optional<Path> modelPath = paths
                    .filter(Files::isDirectory) // Only consider directories
                    .filter(p -> p.getFileName().toString().equals(MODELS_FOLDER)) // Match folder named 'models'
                    .findFirst();

            if (modelPath.isPresent()) {
                log.info("[findModelBasePath] 'models' directory found at: {}", modelPath.get());
                return modelPath.get();
            } else {
                String errorMsg = "models directory not found under: " + javaSourceRoot;
                log.error("[findModelBasePath] " + errorMsg);
                throw new IOException(errorMsg);
            }

        } catch (IOException e) {
            log.error("[findModelBasePath] Error while searching for 'models' directory under {}", javaSourceRoot, e);
            throw new RuntimeException(e);
        }
    }

    /**
     * Searches for a matching Sling Model Java file for the given component in the specified models directory.
     * <p>
     * The search prioritizes an exact match (componentName + MODEL_FILE_SUFFIX) and then falls back
     * to partial matches that contain the component name and end with the model suffix.
     * </p>
     *
     * @param modelsDirPath the path to the directory containing Sling Model Java files
     * @param componentName the name of the component whose Sling Model is to be found
     * @return the {@link File} representing the matching Sling Model, or {@code null} if none is found
     */
    private File findMatchingModelFile(String modelsDirPath, String componentName) {
        log.info("[findMatchingModelFile] Searching for Sling Model for component '{}' in directory '{}'",
                componentName, modelsDirPath);

        File dir = new File(modelsDirPath);
        if (!dir.exists() || !dir.isDirectory()) {
            log.info("[findMatchingModelFile] Models directory '{}' does not exist or is not a directory", modelsDirPath);
            return null;
        }

        File[] files = dir.listFiles((d, name) -> name.endsWith(JAVA_EXTENSION));
        if (files == null || files.length == 0) {
            log.info("[findMatchingModelFile] No Java files found in models directory '{}'", modelsDirPath);
            return null;
        }

        String lcComponent = componentName.toLowerCase();

        // Check exact match first
        for (File file : files) {
            String lcFile = file.getName().toLowerCase();
            if (lcFile.equals(lcComponent + MODEL_FILE_SUFFIX)) {
                log.info("[findMatchingModelFile] Exact match found for component '{}' → '{}'", componentName, file.getName());
                return file;
            }
        }

        // Check partial matches
        for (File file : files) {
            String lcFile = file.getName().toLowerCase();
            if (lcFile.contains(lcComponent) && lcFile.endsWith(MODEL_FILE_SUFFIX)) {
                log.info("[findMatchingModelFile] Partial match found for component '{}' → '{}'", componentName, file.getName());
                return file;
            }
        }

        log.info("[findMatchingModelFile] No matching Sling Model found for component '{}'", componentName);
        return null;
    }

    /**
     * Copies a Sling Model Java file and its dependent models to the target package.
     * <p>
     * This method performs the following steps:
     * <ol>
     *     <li>Checks if the model file exists and if it has already been copied.</li>
     *     <li>Updates the package declaration to the target package.</li>
     *     <li>Updates import statements to reference the target package.</li>
     *     <li>Writes the updated model file to the target directory.</li>
     *     <li>Recursively copies all dependent Sling Models referenced in the model.</li>
     * </ol>
     * </p>
     *
     * @param modelFile         the source Sling Model Java file to copy
     * @param sourceBase        the base directory of the source Sling Models
     * @param targetBase        the target directory to copy the model into
     * @param targetPackageName the target Java package name
     * @param copiedModels      a {@link Set} tracking already copied models to avoid duplication
     * @throws IOException if reading or writing files fails
     */
    private void copyModelAndDependencies(File modelFile, String sourceBase, String targetBase,
                                          String targetPackageName, Set<String> copiedModels) throws IOException {
        if (modelFile == null || !modelFile.exists()) {
            log.info("[copyModelAndDependencies] Model file is null or does not exist: {}", modelFile);
            return;
        }

        String modelName = modelFile.getName();
        if (copiedModels.contains(modelName)) {
            log.info("[copyModelAndDependencies] Model '{}' already copied, skipping", modelName);
            return;
        }

        log.info("[copyModelAndDependencies] Copying Sling Model '{}'", modelName);

        String originalContent = FileUtils.readFileToString(modelFile, UTF_8);

        // Update package declaration
        String content = originalContent.replaceFirst(
                PACKAGE_DECLARATION_REGEX,
                "package " + targetPackageName + ";"
        );

        // Update import statements
        Pattern importPattern = Pattern.compile(IMPORT_STATEMENT_REGEX);
        Matcher importMatcher = importPattern.matcher(content);
        StringBuffer updatedContent = new StringBuffer();
        while (importMatcher.find()) {
            String className = importMatcher.group(1);
            String newImport = "import " + targetPackageName + "." + className + ";";
            importMatcher.appendReplacement(updatedContent, Matcher.quoteReplacement(newImport));
            log.info("[copyModelAndDependencies] Updated import for '{}' in model '{}'", className, modelName);
        }
        importMatcher.appendTail(updatedContent);
        content = updatedContent.toString();

        // Write updated model to target
        File destFile = new File(targetBase, modelFile.getName());
        destFile.getParentFile().mkdirs();
        FileUtils.writeStringToFile(destFile, content, UTF_8);
        copiedModels.add(modelName);
        log.info("[copyModelAndDependencies] Sling Model '{}' copied to '{}'", modelName, destFile.getAbsolutePath());

        // Recursively copy dependent models
        Set<String> dependentTypes = extractReferencedModelTypes(originalContent);
        for (String type : dependentTypes) {
            File depFile = new File(sourceBase, type + JAVA_EXTENSION);
            if (depFile.exists()) {
                log.info("[copyModelAndDependencies] Copying dependent model '{}' for '{}'", type, modelName);
                copyModelAndDependencies(depFile, sourceBase, targetBase, targetPackageName, copiedModels);
            } else {
                log.info("[copyModelAndDependencies] Dependent model '{}' not found for '{}'", type, modelName);
            }
        }
    }

    /**
     * Extracts the names of Sling Model classes referenced within a given Java source content.
     * <p>
     * This method detects references in two ways:
     * <ol>
     *     <li>Via import statements matching {@code IMPORT_STATEMENT_REGEX}.</li>
     *     <li>Via usage of class names that exist in the Sling Models source directory.</li>
     * </ol>
     * </p>
     *
     * @param content the Java source code content to analyze
     * @return a {@link Set} of referenced Sling Model class names
     */
    private Set<String> extractReferencedModelTypes(String content) {
        Set<String> types = new HashSet<>();

        // 1. Extract types via import statements
        Pattern importPattern = Pattern.compile(IMPORT_STATEMENT_REGEX);
        Matcher importMatcher = importPattern.matcher(content);
        while (importMatcher.find()) {
            String type = importMatcher.group(1);
            types.add(type);
            log.info("[extractReferencedModelTypes] Found referenced model via import: {}", type);
        }

        // 2. Extract types via direct usage in code
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
                        log.info("[extractReferencedModelTypes] Found referenced model via usage: {}", className);
                    }
                }
            }
        } else {
            log.info("[extractReferencedModelTypes] Sling Models directory not found: {}", modelsDir.getAbsolutePath());
        }

        return types;
    }

    /**
     * Extracts the fully qualified class name (FQCN) from a given Java file.
     * <p>
     * The FQCN is constructed using the provided target package and the public class
     * name declared in the Java file.
     * </p>
     *
     * @param javaFile      the Java source file to extract the class name from
     * @param targetPackage the target package name to prepend to the class name
     * @return the fully qualified class name, or {@code null} if not found
     */
    private String extractFullyQualifiedClassName(File javaFile, String targetPackage) {
        if (javaFile == null || !javaFile.exists()) {
            log.info("[extractFullyQualifiedClassName] Java file does not exist: {}", javaFile);
            return null;
        }

        try {
            String content = FileUtils.readFileToString(javaFile, "UTF-8");
            Pattern classPattern = Pattern.compile(CLASS_DECLARATION_REGEX);
            Matcher matcher = classPattern.matcher(content);

            if (matcher.find()) {
                String className = matcher.group(1);
                String fqcn = targetPackage + "." + className;
                log.info("[extractFullyQualifiedClassName] Extracted FQCN '{}' from file '{}'", fqcn, javaFile.getAbsolutePath());
                return fqcn;
            } else {
                log.info("[extractFullyQualifiedClassName] No public class found in file '{}'", javaFile.getAbsolutePath());
            }
        } catch (IOException e) {
            log.error("[extractFullyQualifiedClassName] Failed to extract FQCN from file '{}'", javaFile.getAbsolutePath(), e);
        }
        return null;
    }

    /**
     * Retrieves the list of component groups for the specified AEM project.
     * <p>
     * This method reads the application title from the POM file (if available),
     * determines the components folder path, and recursively collects all
     * component groups. Technical or hidden groups (like structure or form groups)
     * are excluded from the final list.
     * </p>
     *
     * @param projectName the name of the project for which to fetch component groups
     * @return a list of component group names, or a single entry containing the
     * project/app title if no groups are found
     */
    @Override
    public List<String> getComponentGroups(String projectName) {
        log.info("[getComponentGroups] Starting to fetch component groups for project '{}'", projectName);

        // Resolve application title from POM or fallback to project name
        String appTitle = readAppTitleFromPom(projectName);
        if (appTitle == null || appTitle.isBlank()) {
            log.info("[getComponentGroups] App title not found in POM, using project name '{}'", projectName);
            appTitle = projectName;
        }
        String appId = AemUtil.getAppId(PROJECTS_DIR, projectName);

        // Resolve the absolute components folder path
        String componentsPath = PROJECTS_DIR + "/" + projectName +"/" + CONTENT_ROOT_PATH + "/" + appId + "/" + COMPONENTS_FOLDER;
        log.info("[getComponentGroups] Components folder path resolved to '{}'", componentsPath);

        File folder = new File(componentsPath);
        Set<String> groups = new HashSet<>();
        groups.add(appTitle); // Ensure app title is always present as a group

        // Collect groups if the folder exists
        if (folder.exists() && folder.isDirectory()) {
            log.info("[getComponentGroups] Collecting component groups from folder '{}'", componentsPath);
            collectComponentGroupsRecursive(folder, groups);
            log.info("[getComponentGroups] Groups collected (before filtering): {}", groups);
        } else {
            log.info("[getComponentGroups] Components folder '{}' does not exist for project '{}'", componentsPath, projectName);
        }

        // Filter out technical/hidden groups
        final String finalAppTitle = appTitle;
        groups.removeIf(g -> {
            String trimmed = g.trim();
            boolean remove = trimmed.equals(finalAppTitle + STRUCTURE_GROUP_SUFFIX)
                    || trimmed.equals(HIDDEN_GROUP)
                    || trimmed.contains(FORM_GROUP_SUFFIX);
            if (remove) {
                log.info("[getComponentGroups] Excluding group '{}' as technical or hidden", trimmed);
            }
            return remove;
        });
        log.info("[getComponentGroups] Groups after filtering: {}", groups);

        // Finalize the result, falling back to app title if no groups remain
        List<String> result = groups.isEmpty() ? List.of(appTitle) : new ArrayList<>(groups);
        log.info("[getComponentGroups] Final component groups for project '{}': {}", projectName, result);

        return result;
    }

    /**
     * Recursively collects component group names from a directory and its subdirectories.
     * <p>
     * This method scans each folder for a {@code .content.xml} file and extracts the
     * {@code componentGroup} property. All found groups are added to the provided {@code groups} set.
     * Excluded folders are skipped, and filtering of technical/hidden groups is performed later.
     * </p>
     *
     * @param dir    the directory to scan for component groups
     * @param groups the set to collect component group names
     */
    private void collectComponentGroupsRecursive(File dir, Set<String> groups) {
        if (!dir.isDirectory()) {
            log.info("[collectComponentGroupsRecursive] Skipping non-directory: {}", dir.getAbsolutePath());
            return;
        }

        String dirName = dir.getName();
        if (EXCLUDED_FOLDERS.stream().anyMatch(ex -> dirName.equalsIgnoreCase(ex) || dirName.startsWith(ex))) {
            log.info("[collectComponentGroupsRecursive] Skipping excluded folder '{}'", dir.getAbsolutePath());
            return;
        }

        File contentXml = new File(dir, CONTENT_XML);
        if (contentXml.exists()) {
            String content = FileGenerationUtil.readFile(contentXml);
            String group = extractProperty(content, COMPONENT_GROUP).trim();
            if (!group.isEmpty()) {
                log.info("[collectComponentGroupsRecursive] Found component group '{}' in folder '{}'", group, dir.getAbsolutePath());
                groups.add(group); // Add all groups; filtering is done later
            } else {
                log.info("[collectComponentGroupsRecursive] No componentGroup property found in '{}'", contentXml.getAbsolutePath());
            }
        }

        File[] subDirs = dir.listFiles(File::isDirectory);
        if (subDirs != null) {
            for (File subDir : subDirs) {
                collectComponentGroupsRecursive(subDir, groups);
            }
        } else {
            log.info("[collectComponentGroupsRecursive] No subdirectories found in '{}'", dir.getAbsolutePath());
        }
    }

    /**
     * Reads the application title from the project's pom.xml file.
     * <p>
     * The method looks for the {@code <componentGroupName>} element inside the POM.
     * If found, it returns the trimmed text content; otherwise, it returns {@code null}.
     * </p>
     *
     * @param projectName the name of the project whose POM is to be read
     * @return the application title from the POM, or {@code null} if not found or on error
     */
    public String readAppTitleFromPom(String projectName) {
        File pom = new File(PROJECTS_DIR + "/" + projectName + "/" + POM_FILE_NAME);

        if (!pom.exists()) {
            log.info("[readAppTitleFromPom] pom.xml not found for project '{}'", projectName);
            return null;
        }

        try {
            Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(pom);
            doc.getDocumentElement().normalize();

            Node node = doc.getElementsByTagName("componentGroupName").item(0);
            if (node != null) {
                String title = node.getTextContent().trim();
                log.info("[readAppTitleFromPom] Read app title '{}' from pom.xml for project '{}'", title, projectName);
                return title;
            } else {
                log.info("[readAppTitleFromPom] No <componentGroupName> element found in pom.xml for project '{}'", projectName);
                return null;
            }
        } catch (Exception e) {
            log.error("[readAppTitleFromPom] Failed to read app title from pom.xml for project '{}'", projectName, e);
            return null;
        }
    }

    /**
     * Generates a new AEM component for the given project based on the provided request.
     * <p>
     * This method delegates the actual file generation to {@link FileGenerationUtil#generateAllFiles(String, ComponentRequest)}.
     * Logs are added before and after generation for tracking purposes.
     * </p>
     *
     * @param projectName the name of the project where the component will be generated
     * @param request     the component request containing details like component name and configuration
     */
    @Override
    public void generateComponent(String projectName, ComponentRequest request) {
        String componentName = request.getComponentName();
        log.info("[generateComponent] Generating component '{}' for project '{}'", componentName, projectName);

        FileGenerationUtil.generateAllFiles(projectName, request);

        log.info("[generateComponent] Component '{}' generation completed for project '{}'", componentName, projectName);
    }

    /**
     * Checks if a component name is available for creation in a given project.
     * <p>
     * The method resolves the exact path of the component using {@link #findComponentPathExact(String, String)}.
     * If the path does not exist or is empty, the component is considered available.
     * </p>
     *
     * @param projectName   the project in which to check for component availability
     * @param componentName the component name to check
     * @return {@code true} if the component name is available, {@code false} otherwise
     */
    @Override
    public boolean isComponentNameAvailable(String projectName, String componentName) {

        String basePath = findComponentPathExact(projectName, componentName);

        if (basePath == null || basePath.isBlank()) {
            log.info("[isComponentNameAvailable] Base path not found for project '{}' and component '{}'. Assuming available.", projectName, componentName);
            return true;
        }

        File componentDir = new File(basePath);

        if (componentDir.exists() && componentDir.isDirectory()) {
            log.info("[isComponentNameAvailable] Component '{}' already exists at path '{}'", componentName, basePath);
            return false;
        }

        log.info("[isComponentNameAvailable] Component '{}' is available at path '{}'", componentName, basePath);
        return true;
    }

    /**
     * Retrieves all components of a project grouped by their component groups.
     * <p>
     * The method constructs the components folder path using the project name and app ID,
     * then recursively scans for components, grouping them by their respective component group names.
     * </p>
     *
     * @param projectName the name of the project whose components are to be fetched
     * @return a map where the key is the component group name and the value is a list of component names
     */
    @Override
    public Map<String, List<String>> getComponentsByGroup(String projectName) {

        String appId = AemUtil.getAppId(PROJECTS_DIR, projectName);

        String componentsPath = PROJECTS_DIR + "/" + projectName + "/" + CONTENT_ROOT_PATH + "/" + appId + "/" + COMPONENTS_FOLDER;

        log.info("[getComponentsByGroup] Fetching components for project '{}' from '{}'", projectName, componentsPath);

        Map<String, List<String>> groupedComponents = new HashMap<>();

        scanComponents(new File(componentsPath), groupedComponents, APPS_PATH_PREFIX + appId + "/" + COMPONENTS_FOLDER);

        log.info("[getComponentsByGroup] Completed fetching components. Total groups found: {}", groupedComponents.size());

        return groupedComponents;
    }


    /**
     * Recursively scans a folder for AEM components and groups them by their component group names.
     * <p>
     * Components are identified by the presence of a `.content.xml` file and validated via {@link #isComponent(File)}.
     * Skips any folders listed in {@link com.aem.builder.constants.ComponentConstants#EXCLUDED_FOLDERS}.
     * Updates the provided map with component group names as keys
     * and a list of relative component paths as values.
     * </p>
     *
     * @param folder            the folder to scan for components
     * @param groupedComponents the map to populate with components grouped by their component groups
     * @param basePath          the base path used to calculate relative component paths
     */
    private void scanComponents(File folder, Map<String, List<String>> groupedComponents, String basePath) {
        if (folder == null || !folder.exists() || !folder.isDirectory()) {
            log.info("[scanComponents] Folder does not exist or is not a directory: {}", folder);
            return;
        }

        File[] files = folder.listFiles();
        if (files == null) return;

        for (File file : files) {
            if (!file.isDirectory()) continue;

            String name = file.getName();

            if (EXCLUDED_FOLDERS.stream().anyMatch(ex -> name.equalsIgnoreCase(ex) || name.startsWith(ex))) {
                log.info("[scanComponents] Skipping excluded folder: {}", name);
                continue;
            }

            File contentXml = new File(file, ".content.xml");

            if (contentXml.exists() && isComponent(contentXml)) {
                String group = getComponentGroup(contentXml);
                if (group == null) {
                    log.info("[scanComponents] Skipping hidden or invalid component in folder: {}", file.getAbsolutePath());
                    continue;
                }

                groupedComponents.computeIfAbsent(group, k -> new ArrayList<>());

                String relativePath = basePath + "/" + name;

                if (!groupedComponents.get(group).contains(relativePath)) {
                    groupedComponents.get(group).add(relativePath);
                    log.info("[scanComponents] Added component '{}' under group '{}'", relativePath, group);
                }
            }

            // Recurse into subdirectories
            scanComponents(file, groupedComponents, basePath + "/" + name);
        }
    }

    /**
     * Determines whether a given .content.xml file represents a valid AEM component.
     * <p>
     * This method parses the XML and checks if the root element's <code>jcr:primaryType</code>
     * matches the constant <code>CQ_COMPONENT_PRIMARY_TYPE</code> defined in {@link com.aem.builder.constants.ComponentConstants}.
     * Logs are generated at the start, during validation, and on success or failure.
     * </p>
     *
     * @param contentXml The .content.xml file to check.
     * @return {@code true} if the file represents a valid AEM component; {@code false} otherwise.
     */
    private boolean isComponent(File contentXml) {
        log.info("[isComponent] Checking if file '{}' is an AEM component", contentXml);

        if (contentXml == null || !contentXml.exists() || !contentXml.isFile()) {
            log.info("[isComponent] Invalid .content.xml file: {}", contentXml);
            return false;
        }

        try {
            Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(contentXml);
            Element root = doc.getDocumentElement();

            String primaryType = root.getAttribute(JCR_PRIMARY_TYPE);
            boolean isComponent = CQ_COMPONENT_PRIMARY_TYPE.equals(primaryType);

            log.info("[isComponent] Checked file '{}', jcr:primaryType='{}', isComponent={}",
                    contentXml.getAbsolutePath(), primaryType, isComponent);

            log.info("[isComponent] Result for file '{}': {}", contentXml.getAbsolutePath(), isComponent);
            return isComponent;

        } catch (Exception e) {
            log.error("[isComponent] Error parsing .content.xml file '{}'", contentXml.getAbsolutePath(), e);
            return false;
        }
    }

    /**
     * Retrieves the component group for a given .content.xml file.
     * <p>
     * Parses the XML file and returns the value of the <code>componentGroup</code> attribute.
     * If the component is marked as hidden (via {@link com.aem.builder.constants.ComponentConstants#HIDDEN_GROUP}),
     * this method returns {@code null}. If no group is defined, returns the default group
     * ({@link com.aem.builder.constants.ComponentConstants#DEFAULT_COMPONENT_GROUP}).
     * </p>
     *
     * @param contentXml The .content.xml file of the component.
     * @return The component group name, {@code null} if hidden, or the default group if undefined.
     */
    private String getComponentGroup(File contentXml) {
        log.info("[getComponentGroup] Fetching component group for file '{}'", contentXml);

        if (contentXml == null || !contentXml.exists() || !contentXml.isFile()) {
            log.info("[getComponentGroup] Invalid .content.xml file: {}", contentXml);
            return DEFAULT_COMPONENT_GROUP;
        }

        try {
            Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(contentXml);
            Element root = doc.getDocumentElement();

            if (root.hasAttribute(COMPONENT_GROUP)) {
                String group = root.getAttribute(COMPONENT_GROUP).trim();

                if (HIDDEN_GROUP.equalsIgnoreCase(group)) {
                    log.info("[getComponentGroup] Component '{}' is hidden; skipping group.", contentXml.getName());
                    return null;
                }

                log.info("[getComponentGroup] Component '{}' belongs to group '{}'", contentXml.getName(), group);
                return group;
            }
        } catch (Exception e) {
            log.error("[getComponentGroup] Error reading component group from '{}'", contentXml.getAbsolutePath(), e);
        }

        log.info("[getComponentGroup] Component '{}' has no group defined; assigning default group '{}'",
                contentXml.getName(), DEFAULT_COMPONENT_GROUP);
        return DEFAULT_COMPONENT_GROUP;
    }

    /**
     * Searches for the exact path of a component within a given AEM project.
     * <p>
     * The method constructs the components root folder path based on the project name and app ID,
     * then recursively searches for a component folder whose name exactly matches the provided component name.
     * </p>
     *
     * @param projectName   The name of the AEM project.
     * @param componentName The exact name of the component to find.
     * @return The absolute path of the component if found; {@code null} otherwise.
     */
    public String findComponentPathExact(String projectName, String componentName) {
        log.info("[findComponentPathExact] Searching for component '{}' in project '{}'", componentName, projectName);

        String appId = AemUtil.getAppId(PROJECTS_DIR, projectName);

        File componentsRoot = new File(PROJECTS_DIR + "/" + projectName
                + "/" + CONTENT_ROOT_PATH + "/" + appId + "/" + COMPONENTS_FOLDER);

        log.info("[findComponentPathExact] Components root path: '{}'", componentsRoot.getAbsolutePath());

        if (componentsRoot.exists() && componentsRoot.isDirectory()) {
            String foundPath = searchComponentRecursiveExact(componentsRoot, componentName);

            if (foundPath != null) {
                log.info("[findComponentPathExact] Component '{}' found at '{}'", componentName, foundPath);
            } else {
                log.info("[findComponentPathExact] Component '{}' not found in project '{}'", componentName, projectName);
            }
            return foundPath;
        } else {
            log.error("[findComponentPathExact] Components root folder does not exist for project '{}': {}",
                    projectName, componentsRoot.getAbsolutePath());
            return null;
        }
    }

    /**
     * Recursively searches for a component folder that exactly matches the given component name.
     * <p>
     * Performs a case-sensitive match against folder names. If the component folder is found, returns its absolute path.
     * Recurses into all subdirectories otherwise.
     * </p>
     *
     * @param dir           The directory to start searching from.
     * @param componentName The exact name of the component to search for.
     * @return The absolute path of the matching component folder if found; {@code null} otherwise.
     */
    public String searchComponentRecursiveExact(File dir, String componentName) {
        log.info("[searchComponentRecursiveExact] Searching for component '{}' in directory '{}'",
                componentName, dir != null ? dir.getPath() : "null");

        if (dir == null || !dir.isDirectory()) {
            log.info("[searchComponentRecursiveExact] Skipping non-directory or null path: {}",
                    (dir != null ? dir.getPath() : "null"));
            return null;
        }

        // Exact case-sensitive match
        if (dir.getName().equals(componentName)) {
            log.info("[searchComponentRecursiveExact] Exact match found for component '{}' at '{}'",
                    componentName, dir.getPath());
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

        log.trace("[searchComponentRecursiveExact] Component '{}' not found in directory '{}'",
                componentName, dir.getPath());
        return null;
    }

    /**
     * Fetches the super types of all components in the specified AEM project.
     * <p>
     * Iterates through all components, reads their `.content.xml` files, and extracts the
     * `sling:resourceSuperType` attribute. Returns a map where the key is the component path
     * or supertype path, and the value is the last segment of the component/supertype name.
     * Handles missing `.content.xml` files or components gracefully.
     * </p>
     *
     * @param projectName The name of the project to fetch component supertypes from.
     * @return A map of component or supertype paths to their last path segment, sorted alphabetically.
     */
    @Override
    public Map<String, String> fetchComponentSuperTypes(String projectName) {
        log.info("[fetchComponentSuperTypes] Starting to fetch component supertypes for project '{}'", projectName);

        Map<String, String> superTypeMap = new LinkedHashMap<>();
        final String CONTENT_XML = ".content.xml";
        final String ATTR_SUPER_TYPE = "sling:resourceSuperType";

        try {
            Map<String, String> components = fetchComponentsWithGroups(projectName);
            log.info("[fetchComponentSuperTypes] Found {} components in project '{}'", components.size(), projectName);

            for (String componentName : components.keySet()) {
                String componentPath = findComponentPathExact(projectName, componentName);
                if (componentPath == null) {
                    log.info("[fetchComponentSuperTypes] Component '{}' not found under project '{}'", componentName, projectName);
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
                            log.info("[fetchComponentSuperTypes] Found supertype '{}' for component '{}'", superType, componentName);
                        }
                    } catch (Exception e) {
                        log.error("[fetchComponentSuperTypes] Error parsing '{}' for component '{}'", CONTENT_XML, componentName, e);
                    }
                } else {
                    log.info("[fetchComponentSuperTypes] '{}' not found for component '{}'", CONTENT_XML, componentName);
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

            log.info("[fetchComponentSuperTypes] Successfully fetched supertypes for project '{}'", projectName);
        } catch (Exception e) {
            log.error("[fetchComponentSuperTypes] Error fetching component supertypes for project '{}'", projectName, e);
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
     * Adds a key-value mapping to the provided map if neither the key nor the value already exists.
     * <p>
     * This ensures that duplicate keys or values are not inserted. Logs addition and skip decisions
     * for traceability.
     * </p>
     *
     * @param map   The map to update.
     * @param key   The key to add.
     * @param value The value to add.
     */
    private void putIfNotExists(Map<String, String> map, String key, String value) {
        log.info("[putIfNotExists] Attempting to add mapping [{}] -> [{}]", key, value);

        if (!map.containsKey(key) && !map.containsValue(value)) {
            map.put(key, value);
            log.info("[putIfNotExists] Added mapping: [{}] -> [{}]", key, value);
        } else {
            log.trace("[putIfNotExists] Skipping duplicate entry for key [{}] or value [{}]", key, value);
        }
    }

    /**
     * Extracts a version-aware component name from a given superType path.
     * <p>
     * If the superType path contains a version segment (e.g., "v1"), it appends the version
     * in parentheses to the last segment of the path. Otherwise, returns the last segment.
     * </p>
     *
     * @param superTypePath The full superType path (e.g., "/apps/project/components/v1/button").
     * @return The version-aware name (e.g., "button (v1)") or the last segment if no version detected.
     */
    private String extractVersionAwareName(String superTypePath) {
        final String VERSION_PATTERN = "v\\d+";

        log.info("[extractVersionAwareName] Extracting version-aware name from '{}'", superTypePath);

        if (superTypePath == null || superTypePath.isBlank()) {
            log.info("[extractVersionAwareName] Received blank or null superTypePath.");
            return "";
        }

        String[] parts = superTypePath.split("/");
        if (parts.length >= 2) {
            String last = parts[parts.length - 1];
            String secondLast = parts[parts.length - 2];

            if (secondLast.matches(VERSION_PATTERN)) {
                String result = last + " (" + secondLast + ")";
                log.info("[extractVersionAwareName] Extracted version-aware name '{}' from '{}'", result, superTypePath);
                return result;
            }

            log.info("[extractVersionAwareName] Extracted simple name '{}' from '{}'", last, superTypePath);
            return last;
        }

        log.info("[extractVersionAwareName] Could not parse '{}', returning as-is.", superTypePath);
        return superTypePath;
    }

    /**
     * Retrieves all parent tabs for a given component superType in a project.
     * <p>
     * This method recursively collects tabs from the specified superType and aggregates them
     * into a set to avoid duplicates. The resulting map contains a flag indicating whether any
     * tabs were found and a list of the collected tab names.
     * </p>
     *
     * @param projectName The name of the AEM project.
     * @param superType   The sling:resourceSuperType of the component to fetch tabs for.
     * @return A map containing:
     * <ul>
     *     <li>"hasTabs": {@code true} if any tabs were found; {@code false} otherwise.</li>
     *     <li>"tabs": A list of tab names (empty if none found).</li>
     * </ul>
     */
    @Override
    public Map<String, Object> getParentTabs(String projectName, String superType) {
        log.info("[getParentTabs] Fetching parent tabs for superType '{}' in project '{}'", superType, projectName);

        Map<String, Object> result = new HashMap<>();
        Set<String> tabs = new LinkedHashSet<>();

        try {
            collectTabsRecursively(projectName, superType, tabs);

            boolean hasTabs = !tabs.isEmpty();
            result.put("hasTabs", hasTabs);
            result.put("tabs", new ArrayList<>(tabs));

            log.info("[getParentTabs] Final merged tabs for superType '{}' in project '{}': {}",
                    superType, projectName, tabs);

        } catch (Exception e) {
            log.error("[getParentTabs] Error while fetching parent tabs for superType '{}' in project '{}'",
                    superType, projectName, e);

            result.put("hasTabs", false);
            result.put("tabs", Collections.emptyList());
        }

        return result;
    }

    /**
     * Recursively collects all tabs defined for a component and its parent superTypes.
     * <p>
     * This method traverses the superType hierarchy of a component, parsing dialog files
     * to collect tab names. Tabs from core components and project-specific components are
     * merged into the provided {@code tabs} set to avoid duplicates.
     * </p>
     *
     * @param projectName The name of the AEM project.
     * @param superType   The sling:resourceSuperType of the component whose tabs are being collected.
     * @param tabs        A set to accumulate tab names; duplicates are ignored.
     * @throws Exception If there is an error reading or parsing dialog files.
     */
    private void collectTabsRecursively(String projectName, String superType, Set<String> tabs) throws Exception {
        log.info("[collectTabsRecursively] Collecting tabs for superType '{}' in project '{}'", superType, projectName);

        if (superType == null || superType.isBlank()) {
            log.info("[collectTabsRecursively] Skipping empty superType for project '{}'", projectName);
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
                log.info("[collectTabsRecursively] Tabs collected from '{}' [{}]: {}", superType, (isCore ? "core" : "project"), currentTabs);
            }
        }

        File compContentFile = new File(basePath, CONTENT_XML);
        if (!compContentFile.exists()) {
            log.info("[collectTabsRecursively] No .content.xml found for '{}'", superType);
            return;
        }

        String parentSuperType = readSuperType(compContentFile);
        if (parentSuperType == null || parentSuperType.isBlank()) {
            log.info("[collectTabsRecursively] No parent superType defined for '{}'", superType);
            return;
        }

        log.info("[collectTabsRecursively] '{}' extends '{}'", superType, parentSuperType);

        if (parentSuperType.startsWith(CORE_PREFIX)) {
            String corePath = System.getProperty(USER_DIR_SYS_PROP) + CORE_RESOURCE_PATH_PREFIX + parentSuperType;
            File coreDialog = new File(corePath, DIALOG_FILE);

            if (coreDialog.exists()) {
                List<String> coreTabs = parseCoreTabsFromDialog(coreDialog);
                if (!coreTabs.isEmpty()) {
                    tabs.addAll(coreTabs);
                    log.info("[collectTabsRecursively] Core Tabs collected from '{}': {}", parentSuperType, coreTabs);
                }
            } else {
                log.info("[collectTabsRecursively] Core dialog not found for '{}': {}", parentSuperType, coreDialog.getAbsolutePath());
            }
        } else {
            collectTabsRecursively(projectName, parentSuperType, tabs);
        }
    }

    /**
     * Parses a project-specific dialog file to extract all defined tab titles.
     * <p>
     * The method searches for nodes with sling:resourceType = "granite/ui/components/coral/foundation/tabs"
     * and then iterates through their child items to collect tab titles from granite containers.
     * </p>
     *
     * @param dialogFile The dialog XML file to parse.
     * @return A list of tab titles defined in the dialog. Returns an empty list if no tabs are found.
     * @throws Exception If an error occurs while reading or parsing the dialog file.
     */
    private List<String> parseProjectTabsFromDialog(File dialogFile) throws Exception {
        log.info("[parseProjectTabsFromDialog] Parsing project dialog for tabs: {}", dialogFile.getAbsolutePath());

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
                log.info("[parseProjectTabsFromDialog] Found granite tabs node at index {}", i);

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
                            log.info("[parseProjectTabsFromDialog] ➕ Project Tab detected: {}", tabTitle);
                        }
                    }
                }
            }
        }

        if (tabs.isEmpty()) {
            log.info("[parseProjectTabsFromDialog] No project tabs found in {}", dialogFile.getName());
        } else {
            log.info("[parseProjectTabsFromDialog] Total project tabs collected: {}", tabs.size());
        }

        return tabs;
    }

    /**
     * Parses a core AEM dialog file to extract all tab titles recursively.
     * <p>
     * This method handles core components where tabs can be nested within containers.
     * It delegates the recursive extraction to {@link #parseTabsRecursive(org.w3c.dom.Node, List)}.
     * </p>
     *
     * @param dialogFile The core dialog XML file to parse.
     * @return A list of tab titles found in the core dialog. Returns an empty list if none are found.
     * @throws Exception If an error occurs while reading or parsing the dialog file.
     */
    private List<String> parseCoreTabsFromDialog(File dialogFile) throws Exception {
        log.info("[parseCoreTabsFromDialog] Parsing core dialog for tabs: {}", dialogFile.getAbsolutePath());

        List<String> tabs = new ArrayList<>();
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document doc = builder.parse(dialogFile);

        org.w3c.dom.Node root = doc.getDocumentElement();
        log.info("[parseCoreTabsFromDialog] Starting recursive parsing from root element: {}", root.getNodeName());

        parseTabsRecursive(root, tabs);

        if (tabs.isEmpty()) {
            log.info("[parseCoreTabsFromDialog] No core tabs found in {}", dialogFile.getName());
        } else {
            log.info("[parseCoreTabsFromDialog] Core tabs parsed from {}: {}", dialogFile.getName(), tabs);
        }

        return tabs;
    }


    /**
     * Recursively parses an XML node to extract tab titles for core AEM dialogs.
     * <p>
     * This method traverses all child nodes, identifies <tabs> nodes (or nodes with
     * granite/ui/components/coral/foundation/tabs resource type), and collects the titles
     * of each tab container. It adds detected tab titles to the provided list and
     * continues recursion for nested containers.
     * </p>
     *
     * @param node The XML node to inspect recursively.
     * @param tabs The list to populate with discovered tab titles.
     */
    private void parseTabsRecursive(org.w3c.dom.Node node, List<String> tabs) {
        if (node.getNodeType() != org.w3c.dom.Node.ELEMENT_NODE) {
            return;
        }

        NamedNodeMap attrs = node.getAttributes();

        boolean isTabsNode = TYPE_TABS.equals(node.getNodeName()) ||
                (attrs != null && attrs.getNamedItem(SLING_RESOURCE_TYPE) != null &&
                        GRANITE_TABS.equals(attrs.getNamedItem(SLING_RESOURCE_TYPE).getNodeValue()));

        if (isTabsNode) {
            log.info("[parseTabsRecursive] Found <tabs> node at '{}'", node.getNodeName());

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
                    if (resTypeAttr != null && GRANITE_CONTAINER.equals(resTypeAttr.getNodeValue())) {
                        org.w3c.dom.Node titleAttr = tabAttrs.getNamedItem(JCR_TITLE);
                        String tabTitle = (titleAttr != null) ? titleAttr.getNodeValue() : tabNode.getNodeName();
                        tabs.add(tabTitle);
                        log.info("[parseTabsRecursive] ➕ Core Tab detected: {}", tabTitle);
                    }

                    // Recursive call for nested structures
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
     * Reads the superType of an AEM component from its .content.xml file.
     * <p>
     * This method parses the XML file, inspects all attributes of all nodes, and
     * returns the value of the attribute representing the component's super type.
     * It checks for both the standard {@code sling:resourceSuperType} attribute
     * and any attribute ending with the configured suffix {@code :resourceSuperType}.
     * </p>
     *
     * @param compContentFile The .content.xml file of the component.
     * @return The superType as a string if found; {@code null} otherwise.
     * @throws Exception If there is an error parsing the XML file.
     */
    private String readSuperType(File compContentFile) throws Exception {
        log.info("[readSuperType] Reading superType from file: {}", compContentFile.getAbsolutePath());

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

                    log.info("[readSuperType] Attribute found: {} = {}", name, value);

                    if (SLING_RESOURCE_SUPER_TYPE.equals(name) || name.endsWith(SUFFIX_RESOURCE_SUPER_TYPE)) {
                        log.info("[readSuperType] Found superType: {}", value);
                        return value;
                    }
                }
            }
        }

        log.info("[readSuperType] No sling:resourceSuperType found in {}", compContentFile.getName());
        return null;
    }

    /**
     * Filters and returns a list of editable AEM components from the provided component map.
     * <p>
     * A component is considered editable if its component group is either:
     * <ul>
     *     <li>null</li>
     *     <li>not a structure group (i.e., does not equal "{@code appTitle - Structure}")</li>
     *     <li>not hidden (i.e., does not equal ".hidden")</li>
     * </ul>
     * </p>
     *
     * @param compMap  A map of component paths to their respective groups.
     * @param appTitle The application title used to identify structure groups.
     * @return A list of component paths that are editable.
     */
    public List<String> getEditableComponents(Map<String, String> compMap, String appTitle) {
        log.info("[getEditableComponents] Filtering editable components for app '{}'", appTitle);

        if (compMap == null || compMap.isEmpty()) {
            log.info("[getEditableComponents] Component map is null or empty. Returning empty list.");
            return List.of();
        }

        List<String> editableComponents = compMap.entrySet()
                .stream()
                .filter(entry -> {
                    String group = Optional.ofNullable(entry.getValue())
                            .map(String::trim)
                            .orElse(null);
                    boolean isEditable = group == null
                            || (!group.equals(appTitle + " - Structure") && !group.equals(".hidden"));
                    if (!isEditable) {
                        log.info("[getEditableComponents] Excluding component '{}' in group '{}'", entry.getKey(), group);
                    } else {
                        log.info("[getEditableComponents] Including editable component '{}'", entry.getKey());
                    }
                    return isEditable;
                })
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());

        log.info("[getEditableComponents] Total editable components found: {}", editableComponents.size());
        return editableComponents;
    }

}
