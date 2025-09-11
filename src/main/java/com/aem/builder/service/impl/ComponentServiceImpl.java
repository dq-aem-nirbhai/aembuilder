package com.aem.builder.service.impl;

import com.aem.builder.model.DTO.ComponentField;
import com.aem.builder.model.DTO.ComponentRequest;
import com.aem.builder.model.DTO.OptionItem;
import com.aem.builder.model.Enum.FieldType;
import com.aem.builder.service.ComponentService;
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

@Service
@RequiredArgsConstructor
@Slf4j
public class ComponentServiceImpl implements ComponentService {

    private static final String PROJECTS_DIR = "generated-projects";

    /**
     * Extracts a property value from an xml string, handling AEM typed values like
     * <code>componentGroup="{String}my-group"</code>.
     */
    private String extractProperty(String content, String property) {
        if (content == null)
            return "";
        Pattern p = Pattern.compile(property + "=\"(?:\\{String\\})?([^\"]+)\"");
        Matcher m = p.matcher(content);
        return m.find() ? m.group(1) : "";
    }

    @Override
    public List<String> fetchComponentsFromGeneratedProjects(String projectName) {
        // fetchComponentsWithGroups returns Map<componentName, componentGroup>
        Map<String, String> components = fetchComponentsWithGroups(projectName);

        // Return only the component names as a list
        return new ArrayList<>(components.keySet());
    }

    private static final Set<String> EXCLUDED_FOLDERS = Set.of(
            "_cq_", // prefixes like _cq_design_dialog, _cq_template
            ".", // hidden folders
            "cq:template",
            "new",
            "old",
            "backup");

    @Override
    public Map<String, String> fetchComponentsWithGroups(String projectName) {
        Map<String, String> result = new LinkedHashMap<>();
        File componentsDir = new File(PROJECTS_DIR,
                projectName + "/ui.apps/src/main/content/jcr_root/apps/" + projectName + "/components");
        log.info("componentsDir {}", componentsDir);

        if (componentsDir.exists()) {
            fetchRealComponentsRecursive(componentsDir, result);
        }

        return result;
    }

    private void fetchRealComponentsRecursive(File dir, Map<String, String> result) {
        if (!dir.isDirectory())
            return;

        String dirName = dir.getName();

        // Skip excluded folders
        if (EXCLUDED_FOLDERS.stream().anyMatch(ex -> dirName.equalsIgnoreCase(ex) || dirName.startsWith(ex))) {
            return;
        }

        File contentXml = new File(dir, ".content.xml");
        if (contentXml.exists()) {
            String content = FileGenerationUtil.readFile(contentXml);

            // Extra safety: skip if it's a design dialog
            if (content.contains("Design Dialog") || content.contains("cq/gui/components/authoring/dialog")) {
                return;
            }

            String group = extractProperty(content, "componentGroup").trim();
            // Use only component folder name as key
            result.put(dirName, group);
        }

        File[] subDirs = dir.listFiles(File::isDirectory);
        if (subDirs != null) {
            for (File subDir : subDirs) {
                fetchRealComponentsRecursive(subDir, result);
            }
        }
    }

    @Override
    public List<String> getAllComponents() throws IOException {
        List<String> components = new ArrayList<>();
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        Resource[] resources = resolver.getResources("classpath:/aem-components/*");

        for (Resource resource : resources) {
            components.add(resource.getFilename());
        }
        return components;
    }

    @Override
    public List<String> getCommonComponents(List<String> allComponents, List<String> projectComponents) {
        return allComponents.stream()
                .filter(projectComponents::contains)
                .collect(Collectors.toList());
    }

    @Override
    public List<String> getDistinctComponents(List<String> allComponents, List<String> projectComponents) {
        return allComponents.stream()
                .filter(c -> !projectComponents.contains(c))
                .collect(Collectors.toList());
    }

    @Override
    public List<String> getExistingProjects() {
        List<String> existingProjects = new ArrayList<>();
        File projectsDir = new File(System.getProperty("user.dir") + "/generated-projects/");

        if (projectsDir.exists() && projectsDir.isDirectory()) {

            String[] names = projectsDir.list();

            if (names != null) {
                existingProjects = List.of(names);
            }
        }
        return existingProjects;
    }

    @Override
    public ComponentRequest loadComponent(String projectName, String componentName) {

        String basePath = findComponentPathExact(projectName, componentName);
        String group = "";
        String superType = null;
        List<ComponentField> fields = new ArrayList<>();

        try {
            File contentXml = new File(basePath, ".content.xml");
            if (contentXml.exists()) {
                String content = FileGenerationUtil.readFile(contentXml);
                group = extractProperty(content, "componentGroup").trim();
                superType = extractProperty(content, "sling:resourceSuperType").trim();
            }

            File dialogXml = new File(basePath + "/_cq_dialog/.content.xml");
            if (dialogXml.exists()) {
                DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
                factory.setNamespaceAware(false);
                DocumentBuilder builder = factory.newDocumentBuilder();
                Document doc = builder.parse(dialogXml);
                collectFields(doc.getDocumentElement(), fields);
            }
        } catch (Exception e) {
            log.error("Failed to load component {}", componentName, e);
        }

        ComponentRequest req = new ComponentRequest();
        req.setProjectName(projectName);
        req.setComponentName(componentName);
        req.setComponentGroup(group);
        req.setSuperType(superType);
        req.setFields(fields);
        return req;
    }

    @Override
    public void updateComponent(String projectName, ComponentRequest request) {
        String compPath = PROJECTS_DIR + "/" + projectName + "/ui.apps/src/main/content/jcr_root/apps/" + projectName
                + "/components/" + request.getComponentName();
        try {
            FileUtils.deleteDirectory(new File(compPath));
        } catch (IOException e) {
            log.warn("Could not clean component folder before update", e);
        }
        FileGenerationUtil.generateAllFiles(projectName, request);
    }

    @Override
    public void deleteComponent(String projectName, String componentName) {
        log.info("Deleting component '{}' from project '{}'", componentName, projectName);

        // 1. Find component folder
        String compPath = findComponentPathExact(projectName, componentName);
        if (compPath == null) {
            log.warn("Component '{}' not found in project '{}'", componentName, projectName);
            return;
        }
        log.info("Component folder found at '{}'", compPath);

        // 2. Collect Sling Models from HTL recursively
        Set<String> slingModels = new HashSet<>();
        try {
            slingModels = collectSlingModelsFromHTLFolder(new File(compPath));
        } catch (IOException e) {
            log.error("Failed to read HTL files for component '{}'", componentName, e);
        }
        log.info("Sling Models found in HTL: {}", slingModels);

        // 3. Collect multifield names
        ComponentRequest req = loadComponent(projectName, componentName);
        Set<String> multifieldNames = req.getFields().stream()
                .filter(f -> "multifield".equals(f.getFieldType()))
                .map(ComponentField::getFieldName)
                .collect(Collectors.toSet());

        // 4. Collect all Java classes to delete (main + child classes)
        Set<String> javaClassesToDelete = new HashSet<>();
        Set<String> processedClasses = new HashSet<>();
        Path javaRoot = Paths.get(PROJECTS_DIR, projectName, "core", "src", "main", "java");

        for (String fqcn : slingModels) {
            String simpleName = fqcn.substring(fqcn.lastIndexOf('.') + 1);
            try {
                collectClassAndChildren(javaRoot, simpleName, javaClassesToDelete, processedClasses, multifieldNames);
            } catch (IOException e) {
                log.error("Failed to collect child classes for '{}'", fqcn, e);
            }
        }

        // 5. Delete component folder
        try {
            FileUtils.deleteDirectory(new File(compPath));
            log.info("Deleted component folder '{}'", compPath);
        } catch (IOException e) {
            log.error("Failed to delete component folder '{}'", componentName, e);
        }

        // 6. Delete all collected Java classes
        try (Stream<Path> paths = Files.walk(javaRoot)) {
            paths.filter(p -> javaClassesToDelete.contains(p.getFileName().toString()))
                    .forEach(p -> {
                        try {
                            Files.deleteIfExists(p);
                            log.info("Deleted Java class '{}'", p);
                        } catch (IOException ex) {
                            log.error("Failed to delete Java class '{}'", p, ex);
                        }
                    });
        } catch (IOException e) {
            log.error("Failed to locate model classes for component '{}'", componentName, e);
        }
    }

    /**
     * Recursively collect fully qualified Sling Model class names from HTL files
     */
    private Set<String> collectSlingModelsFromHTLFolder(File dir) throws IOException {
        Set<String> slingModels = new HashSet<>();
        if (!dir.isDirectory())
            return slingModels;

        File[] files = dir.listFiles();
        if (files == null)
            return slingModels;

        for (File file : files) {
            if (file.isDirectory()) {
                slingModels.addAll(collectSlingModelsFromHTLFolder(file));
            } else if (file.isFile() && file.getName().endsWith(".html")) {
                slingModels.addAll(extractSlingModelsFromHTL(file));
            }
        }
        return slingModels;
    }

    /**
     * Extract fully qualified Sling Model class names from HTL
     */
    private Set<String> extractSlingModelsFromHTL(File htlFile) throws IOException {
        Set<String> classes = new HashSet<>();
        List<String> lines = Files.readAllLines(htlFile.toPath());

        for (String line : lines) {
            line = line.trim();
            if (line.contains("data-sly-use")) {
                int eqIndex = line.indexOf("=");
                if (eqIndex > 0) {
                    String ref = line.substring(eqIndex + 1).replaceAll("[\"';]", "").trim();
                    // Remove trailing HTML characters like /> or >
                    ref = ref.replaceAll("[/>].*$", "").trim();
                    if (ref.startsWith("com.")) {
                        classes.add(ref);
                    }
                }
            }
        }
        return classes;
    }

    /**
     * Recursively collect a Java class and all child classes via @ChildResource or
     * multifield variable
     */
    private void collectClassAndChildren(Path javaRoot, String className,
            Set<String> javaClassesToDelete,
            Set<String> processedClasses,
            Set<String> multifieldNames) throws IOException {
        if (processedClasses.contains(className))
            return;
        processedClasses.add(className);

        Path classPath = findJavaClassRecursive(javaRoot, className + ".java");
        if (classPath == null) {
            log.warn("Java class '{}' not found under '{}'", className, javaRoot);
            return;
        }
        log.info("Found Java class '{}' at '{}'", className, classPath);

        javaClassesToDelete.add(classPath.getFileName().toString());

        List<String> lines = Files.readAllLines(classPath);

        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i).trim();

            // 1. Detect @ChildResource field
            if (line.contains("@ChildResource")) {
                // Check the next few lines for field declaration (skip annotations)
                for (int j = i + 1; j < Math.min(i + 4, lines.size()); j++) {
                    String fieldLine = lines.get(j).trim();
                    Matcher m = Pattern.compile(
                            "(?:private|protected|public)?\\s*(?:List<([A-Z]\\w+)>|Set<([A-Z]\\w+)>|([A-Z]\\w+))\\s+\\w+;")
                            .matcher(fieldLine);
                    if (m.find()) {
                        String childClass = m.group(1) != null ? m.group(1)
                                : m.group(2) != null ? m.group(2) : m.group(3);
                        if (childClass != null && !childClass.equals(className)) {
                            log.info("Found @ChildResource child class '{}' in '{}'", childClass, className);
                            collectClassAndChildren(javaRoot, childClass, javaClassesToDelete, processedClasses,
                                    multifieldNames);
                        }
                    }
                }
            }

            // 2. Detect multifield child classes
            for (String mfName : multifieldNames) {
                if (line.matches(".*\\b([A-Z]\\w+)\\s+" + mfName + ";.*")) {
                    String childClassName = line.replaceAll(".*\\b([A-Z]\\w+)\\s+" + mfName + ";.*", "$1");
                    log.info("Found child class '{}' for multifield '{}' in '{}'", childClassName, mfName, className);
                    collectClassAndChildren(javaRoot, childClassName, javaClassesToDelete, processedClasses,
                            multifieldNames);
                }
            }
        }
    }

    /**
     * Recursively find Java class by simple file name under javaRoot
     */
    private Path findJavaClassRecursive(Path javaRoot, String className) throws IOException {
        try (Stream<Path> paths = Files.walk(javaRoot)) {
            return paths.filter(p -> p.getFileName().toString().equals(className))
                    .findFirst()
                    .orElse(null);
        }
    }

    @Override
    public String getComponentHtml(String projectName, String componentName) {

        String componentPathExact = findComponentPathExact(projectName, componentName);
        Path htmlPath = Paths.get(componentPathExact, componentName + ".html");
        try {
            return Files.readString(htmlPath);
        } catch (IOException e) {
            log.error("Failed to read HTML for component {}", componentName, e);
            return "";
        }
    }

    @Override
    public String getComponentJava(String projectName, String componentName) {
        try {
            String htlContent = getComponentHtml(projectName, componentName);

            if (htlContent == null || htlContent.isEmpty()) {
                return "// No HTL found for component: " + componentName;
            }

            // Extract Sling Model class from HTL
            Pattern pattern = Pattern.compile("data-sly-use(?:\\.[A-Za-z0-9_-]+)?\\s*=\\s*\"([^\"]+)\"");
            Matcher matcher = pattern.matcher(htlContent);

            String modelClass = null;
            while (matcher.find()) {
                String candidate = matcher.group(1);
                if (!candidate.endsWith(".html") && candidate.contains(".")) {
                    modelClass = candidate;
                    break;
                }
            }

            if (modelClass == null) {
                return "// No Sling Model binding found in HTL for: " + componentName;
            }

            StringBuilder result = new StringBuilder();
            Set<String> processed = new HashSet<>();

            Deque<String> stack = new ArrayDeque<>();
            stack.push(modelClass);

            while (!stack.isEmpty()) {
                String current = stack.pop();

                // Skip duplicates
                if (!processed.add(current))
                    continue;

                Path javaFile = Paths.get(
                        PROJECTS_DIR, projectName, "core", "src", "main", "java",
                        current.replace(".", "/") + ".java");

                if (!Files.exists(javaFile))
                    continue;

                String code = Files.readString(javaFile);
                String simpleName = current.substring(current.lastIndexOf(".") + 1);
                String packageName = current.substring(0, current.lastIndexOf("."));

                result.append("// -------------------------------------------------\n")
                        .append(simpleName).append(" =====\n\n")
                        .append(code).append("\n\n");

                // 🔍 Find possible child class references
                Matcher refMatcher = Pattern.compile("\\b([A-Z][A-Za-z0-9_]+)\\b").matcher(code);
                while (refMatcher.find()) {
                    String refClass = refMatcher.group(1);
                    if (!refClass.equals(simpleName)) {
                        stack.push(packageName + "." + refClass);
                    }
                }
            }

            return result.toString();

        } catch (Exception e) {
            log.error("Failed while resolving Sling Model for component {}", componentName, e);
            return "// Error while resolving Sling Model: " + e.getMessage();
        }
    }

    private static String capitalize(String input) {
        return (input == null || input.isEmpty()) ? input
                : input.substring(0, 1).toUpperCase() + input.substring(1);
    }

    private String getFieldTypeFromResource(String resourceType) {
        if (resourceType == null || resourceType.isEmpty()) {
            return "";
        }

        // Reverse lookup directly from FieldType enum with control over precedence
        String fieldType = "";
        for (FieldType ft : FieldType.values()) {
            if (ft.getResourceType().equals(resourceType)) {
                // later matches overwrite earlier ones
                fieldType = ft.getType();
            }
        }
        return fieldType;
    }

    private String determineFieldType(Element elem) {
        String resourceType = elem.getAttribute("sling:resourceType");
        String type = getFieldTypeFromResource(resourceType);
        if ("granite/ui/components/coral/foundation/form/multifield".equals(resourceType)) {
            type = "multifield";
        } else if ("granite/ui/components/coral/foundation/form/select".equals(resourceType)
                && "true".equalsIgnoreCase(elem.getAttribute("multiple"))) {
            type = "multiselect";
        }
        if ("cq/gui/components/authoring/dialog/fileupload".equals(resourceType)) {
            String node = elem.getNodeName().toLowerCase();
            if (node.contains("file")) {
                type = "fileupload";
            } else if (node.contains("image")) {
                type = "image";
            }
        }
        return type;
    }

    private List<ComponentField> parseField(Element elem) {
        List<ComponentField> result = new ArrayList<>();

        String fieldLabel = elem.getAttribute("fieldLabel");
        String nameAttr = elem.getAttribute("name");
        String fileRefAttr = elem.getAttribute("fileReferenceParameter");
        String resourceType = elem.getAttribute("sling:resourceType");
        String fieldType = getFieldTypeFromResource(resourceType);

        String fieldName = null;

        // --- Multifield naming resolution
        if ("multifield".equals(fieldType) && (nameAttr == null || nameAttr.isBlank())) {
            NodeList fieldNodes = elem.getElementsByTagName("field");
            if (fieldNodes.getLength() > 0) {
                Element fieldElem = (Element) fieldNodes.item(0);
                String nestedFieldName = fieldElem.getAttribute("name");
                if (nestedFieldName != null && !nestedFieldName.isBlank()) {
                    fieldName = nestedFieldName.startsWith("./") ? nestedFieldName.substring(2) : nestedFieldName;
                }
            }
        }

        // --- Normal field name resolution
        if (fieldName == null && nameAttr != null && !nameAttr.isBlank()) {
            if ("fileupload".equals(fieldType) && fileRefAttr != null && !fileRefAttr.isBlank()) {
                fieldName = fileRefAttr.startsWith("./") ? fileRefAttr.substring(2) : fileRefAttr;
            } else {
                fieldName = nameAttr.startsWith("./") ? nameAttr.substring(2) : nameAttr;
            }
        }

        log.info("Parsing field: label='{}', name='{}', type='{}', resourceType='{}'",
                fieldLabel, fieldName, fieldType, resourceType);

        List<OptionItem> options = null;
        List<ComponentField> nested = null;

        // --- Handle multifield
        if ("multifield".equals(fieldType)) {
            nested = new ArrayList<>();
            NodeList fieldNodes = elem.getElementsByTagName("items");
            if (fieldNodes.getLength() > 0) {
                Element itemsElem = (Element) fieldNodes.item(0);
                collectFields(itemsElem, nested); // ✅ recurse into children
            }
        }

        // --- Handle select / multiselect / radiogroup
        else if ("select".equals(fieldType) || "multiselect".equals(fieldType) || "radiogroup".equals(fieldType)) {
            if ("select".equals(fieldType)) {
                String multipleAttr = elem.getAttribute("multiple");
                if ("true".equalsIgnoreCase(multipleAttr)) {
                    fieldType = "multiselect";
                }
            }
            NodeList itemsNodes = elem.getElementsByTagName("items");
            if (itemsNodes.getLength() > 0) {
                Element itemsElem = (Element) itemsNodes.item(0);
                options = new ArrayList<>();
                NodeList optionNodes = itemsElem.getChildNodes();
                for (int i = 0; i < optionNodes.getLength(); i++) {
                    Node n = optionNodes.item(i);
                    if (n instanceof Element optionElem) {
                        String text = optionElem.getAttribute("text");
                        String value = optionElem.getAttribute("value");
                        if ((text != null && !text.isBlank()) || (value != null && !value.isBlank())) {
                            options.add(new OptionItem(text, value));
                        }
                    }
                }
            }
        }

        // --- Handle tab containers (granite/ui/components/coral/foundation/container)
        else if ("tabs".equals(fieldType)) {
            String jcrTitle = elem.getAttribute("jcr:title");
            if (jcrTitle != null && !jcrTitle.isBlank()) {
                fieldLabel = jcrTitle;
            }
            String tabName = elem.getNodeName();

            nested = new ArrayList<>();
            NodeList childNodes = elem.getChildNodes();
            for (int i = 0; i < childNodes.getLength(); i++) {
                Node n = childNodes.item(i);
                if (n instanceof Element childElem && "items".equals(childElem.getNodeName())) {
                    collectFields(childElem, nested);
                }
            }

            result.add(new ComponentField(fieldLabel, tabName, "tabs", false, nested, null));
            return result;
        }

        // --- Default: simple field
        result.add(new ComponentField(fieldLabel, fieldName, fieldType, false, nested, options));
        return result;
    }

    /**
     * Recursively collects dialog fields from the given parent node.
     * Skips technical containers but continues traversing into their children.
     */
    private void collectFields(Element parent, List<ComponentField> fields) {
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (!(node instanceof Element elem)) {
                continue;
            }

            String resourceType = elem.getAttribute("sling:resourceType");
            String type = determineFieldType(elem);

            // --- Special handling for container nodes ---
            if ("granite/ui/components/coral/foundation/container".equals(resourceType)) {
                String parentResourceType = getParentResourceType(elem);

                log.info("Checking container: nodeName={}, resourceType={}, parentResourceType={}",
                        elem.getNodeName(), resourceType, parentResourceType);

                // If container is not part of tabs, skip it as a field but still traverse
                // inside
                if (!"granite/ui/components/coral/foundation/tabs".equals(parentResourceType)) {
                    log.info("Skipping container '{}' as field, but parsing its children", elem.getNodeName());
                    collectFields(elem, fields);
                    continue;
                }
            }

            // --- Normal field processing ---
            if (!type.isEmpty()) {
                List<ComponentField> parsed = parseField(elem);
                if (parsed != null && !parsed.isEmpty()) {
                    fields.addAll(parsed);
                }
            } else {
                // Recurse into children for nested items
                collectFields(elem, fields);
            }
        }
    }

    /**
     * Finds the nearest ancestor that has a sling:resourceType.
     * Useful because many AEM dialog wrapper nodes (like <items>) don't define one.
     */
    private String getParentResourceType(Element elem) {
        Node parent = elem.getParentNode();
        while (parent != null && parent instanceof Element parentElem) {
            if (parentElem.hasAttribute("sling:resourceType")) {
                return parentElem.getAttribute("sling:resourceType");
            }
            parent = parent.getParentNode();
        }
        return "";
    }

    @Override
    public List<String> getProjectComponentsMap(String projectName) {
        Map<String, String> components = fetchComponentsWithGroups(projectName);
        return new ArrayList<>(components.keySet());
    }

    @Override
    public void addComponentsToExistingProject(String projectName, List<String> selectedComponents) {
        try {
            String baseDir = System.getProperty("user.dir") + "/generated-projects/";
            String contentFolderPath = baseDir + projectName +
                    "/ui.apps/src/main/content/jcr_root/apps/" + projectName + "/components";

            copySelectedComponents(selectedComponents, contentFolderPath, projectName);
            System.out.println(" Selected components copied to content folder in project: " + projectName);

        } catch (Exception e) {
            System.err.println("Error while adding components to project.");
            e.printStackTrace();
        }
    }

    @Override
    public void copySelectedComponents(List<String> selectedComponents, String targetPath, String projectName) {
        if (selectedComponents == null || selectedComponents.isEmpty())
            return;

        String slingModelsSourcePath = System.getProperty("user.dir") + "/src/main/java/com/aem/builder/slingModels";

        Path javaSourceRoot = Paths.get("generated-projects/" + projectName + "/core/src/main/java/");

        // Find models directory
        Path modelPath = findModelBasePath(javaSourceRoot);
        log.info("ModelPath{}", modelPath);

        // Get full model base path
        String modelBasePath = modelPath.toString();

        log.info("ModelBasePath{}", modelBasePath);

        // 5. Convert to Java package name
        String packageName = javaSourceRoot.relativize(modelPath).toString().replace(File.separatorChar, '.');

        Set<String> copiedModels = new HashSet<>();

        for (String component : selectedComponents) {
            try {
                File source = new File("src/main/resources/aem-components/" + component);
                File destination = new File(targetPath + "/" + component);
                if (!source.exists()) {
                    System.err.println("Source component not found: " + source.getAbsolutePath());
                    continue;
                }

                if (destination.exists()) {
                    FileUtils.deleteDirectory(destination);
                }

                FileUtils.copyDirectory(source, destination);
                System.out.println("Copied component: " + component + " → " + destination.getAbsolutePath());

                // Update sling:resourceType in .content.xml
                File contentXml = new File(destination, ".content.xml");
                if (contentXml.exists()) {
                    String content = FileUtils.readFileToString(contentXml, "UTF-8");
                    content = content.replaceAll("sling:resourceType=\"[^\"]+\"",
                            "sling:resourceType=\"" + projectName + "/components/" + component.toLowerCase() + "\"");
                    FileUtils.writeStringToFile(contentXml, content, "UTF-8");
                }

                // Update HTML to use correct model reference
                File html = new File(destination, component + ".html");
                File parentModel = findMatchingModelFile(slingModelsSourcePath, component);

                if (html.exists() && parentModel != null) {
                    String htmlContent = FileUtils.readFileToString(html, "UTF-8");
                    // String fqcn = extractFullyQualifiedClassName(parentModel, "com." +
                    // projectName + ".core.models");
                    String fqcn = extractFullyQualifiedClassName(parentModel, packageName);

                    if (fqcn != null) {
                        htmlContent = htmlContent.replaceAll("data-sly-use\\.model=\"[^\"]+\"",
                                "data-sly-use.model=\"" + fqcn + "\"");
                        FileUtils.writeStringToFile(html, htmlContent, "UTF-8");
                    }
                }

                // Copy model and its dependencies
                if (parentModel != null && parentModel.exists()) {
                    copyModelAndDependencies(parentModel, slingModelsSourcePath, modelBasePath, packageName,
                            copiedModels);

                } else {
                    System.out.println("No matching Sling Model found for: " + component);
                }
            } catch (IOException e) {
                System.err.println("Failed to process component: " + component);
                e.printStackTrace();
            }
        }
    }

    private static Path findModelBasePath(Path javaSourceRoot) {
        try (Stream<Path> paths = Files.walk(javaSourceRoot)) {
            Optional<Path> modelPath = paths
                    .filter(Files::isDirectory)
                    .filter(p -> p.getFileName().toString().equals("models"))
                    .findFirst();

            return modelPath.orElseThrow(() -> new IOException("models directory not found under: " + javaSourceRoot));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private File findMatchingModelFile(String modelsDirPath, String componentName) {
        File dir = new File(modelsDirPath);
        if (!dir.exists() || !dir.isDirectory())
            return null;

        File[] files = dir.listFiles((d, name) -> name.endsWith(".java"));
        if (files == null)
            return null;

        String lcComponent = componentName.toLowerCase();

        // First look for exact match
        for (File file : files) {
            String lcFile = file.getName().toLowerCase();
            if (lcFile.equals(lcComponent + "model.java")) {
                return file;
            }
        }

        // Fallback: partial match
        for (File file : files) {
            String lcFile = file.getName().toLowerCase();
            if (lcFile.contains(lcComponent) && lcFile.endsWith("model.java")) {
                return file;
            }
        }

        return null;
    }

    private void copyModelAndDependencies(File modelFile, String sourceBase, String targetBase,
            String targetPackageName, Set<String> copiedModels) throws IOException {
        if (modelFile == null || !modelFile.exists())
            return;

        String modelName = modelFile.getName();
        if (copiedModels.contains(modelName))
            return;

        String originalContent = FileUtils.readFileToString(modelFile, "UTF-8");

        String content = originalContent.replaceFirst(
                "package\\s+com\\.aem\\.builder\\.[\\w.]+;",
                "package " + targetPackageName + ";");

        // Update import statements for internal model classes
        Pattern importPattern = Pattern.compile("import\\s+com\\.aem\\.builder\\.slingModels\\.(\\w+);");
        Matcher importMatcher = importPattern.matcher(content);
        StringBuffer updatedContent = new StringBuffer();
        while (importMatcher.find()) {
            String className = importMatcher.group(1);
            String newImport = "import " + targetPackageName + "." + className + ";";
            importMatcher.appendReplacement(updatedContent, Matcher.quoteReplacement(newImport));
        }
        importMatcher.appendTail(updatedContent);
        content = updatedContent.toString();

        // Write to destination file
        File destFile = new File(targetBase, modelFile.getName());
        destFile.getParentFile().mkdirs();
        FileUtils.writeStringToFile(destFile, content, "UTF-8");
        copiedModels.add(modelName);
        System.out.println("Sling Model copied: " + destFile.getAbsolutePath());

        // Recursively copy dependencies
        Set<String> dependentTypes = extractReferencedModelTypes(originalContent);
        for (String type : dependentTypes) {
            File depFile = new File(sourceBase, type + ".java");
            if (depFile.exists()) {
                copyModelAndDependencies(depFile, sourceBase, targetBase, targetPackageName, copiedModels);
            }
        }

    }

    private Set<String> extractReferencedModelTypes(String content) {
        Set<String> types = new HashSet<>();

        // 1. Check import statements for custom sling models
        Pattern importPattern = Pattern.compile("import\\s+com\\.aem\\.builder\\.slingModels\\.(\\w+);");
        Matcher importMatcher = importPattern.matcher(content);
        while (importMatcher.find()) {
            types.add(importMatcher.group(1));
        }

        File modelsDir = new File(System.getProperty("user.dir") + "/src/main/java/com/aem/builder/slingModels");

        if (modelsDir.exists() && modelsDir.isDirectory()) {
            File[] modelFiles = modelsDir.listFiles((dir, name) -> name.endsWith(".java"));
            if (modelFiles != null) {
                for (File modelFile : modelFiles) {
                    String className = modelFile.getName().replace(".java", "");
                    // Look for direct usage of the class name
                    Pattern usagePattern = Pattern.compile("\\b" + className + "\\b");
                    Matcher usageMatcher = usagePattern.matcher(content);
                    if (usageMatcher.find()) {
                        types.add(className);
                    }
                }
            }
        }

        return types;
    }

    private String extractFullyQualifiedClassName(File javaFile, String targetPackage) {
        try {
            String content = FileUtils.readFileToString(javaFile, "UTF-8");
            Pattern classPattern = Pattern.compile("public\\s+class\\s+(\\w+)");
            Matcher matcher = classPattern.matcher(content);

            if (matcher.find()) {
                String className = matcher.group(1);
                return targetPackage + "." + className;
            }
        } catch (IOException e) {
            System.err.println("Failed to extract FQCN from model file.");
            e.printStackTrace();
        }
        return null;
    }

    @Override
    public List<String> getComponentGroups(String projectName) {
        String appTitle = readAppTitleFromPom(projectName);
        if (appTitle == null || appTitle.isBlank()) {
            appTitle = projectName;
        }

        String path = PROJECTS_DIR + "/" + projectName + "/ui.apps/src/main/content/jcr_root/apps/" + projectName
                + "/components";
        File folder = new File(path);
        Set<String> groups = new HashSet<>();
        groups.add(appTitle);

        if (folder.exists()) {
            collectComponentGroupsRecursive(folder, groups);
        }

        final String finalAppTitle = appTitle;
        // Remove unwanted groups consistently
        groups.removeIf(g -> {
            String trimmed = g.trim();
            return trimmed.equals(finalAppTitle + " - Structure") // exclude Structure
                    || trimmed.equals(".hidden") // exclude hidden
                    || trimmed.contains(" - Form"); // exclude Form
        });

        return groups.isEmpty() ? List.of(appTitle) : new ArrayList<>(groups);
    }

    private void collectComponentGroupsRecursive(File dir, Set<String> groups) {
        if (!dir.isDirectory())
            return;

        String dirName = dir.getName();
        // Skip excluded folders like _cq_, hidden, new/old/backup
        if (EXCLUDED_FOLDERS.stream().anyMatch(ex -> dirName.equalsIgnoreCase(ex) || dirName.startsWith(ex))) {
            return;
        }

        // Check for .content.xml in this folder
        File contentXml = new File(dir, ".content.xml");
        if (contentXml.exists()) {
            String content = FileGenerationUtil.readFile(contentXml);
            String group = extractProperty(content, "componentGroup").trim();
            if (!group.isEmpty()) {
                groups.add(group); // add all groups; exclusion handled later
            }
        }

        // Recurse into subdirectories
        File[] subDirs = dir.listFiles(File::isDirectory);
        if (subDirs != null) {
            for (File subDir : subDirs) {
                collectComponentGroupsRecursive(subDir, groups);
            }
        }
    }

    public String readAppTitleFromPom(String projectName) {
        File pom = new File(PROJECTS_DIR + "/" + projectName + "/pom.xml");

        if (!pom.exists()) {
            return null;
        }
        try {
            Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(pom);
            doc.getDocumentElement().normalize();
            return doc.getElementsByTagName("componentGroupName").item(0).getTextContent();
        } catch (Exception e) {
            System.err.println("Failed to read app title from pom.xml: " + e.getMessage());
            return null;
        }
    }

    @Override
    public void generateComponent(String projectName, ComponentRequest request) {
        FileGenerationUtil.generateAllFiles(projectName, request);
    }

    // component checking
    @Override
    public boolean isComponentNameAvailable(String projectName, String componentName) {
        String basePath = findComponentPathExact(projectName, componentName);

        if (basePath == null || basePath.isBlank()) {
            log.warn("Base path not found for project '{}' and component '{}'", projectName, componentName);
            // If no base path is found, we assume component does not exist → available
            return true;
        }

        File componentDir = new File(basePath);

        // If component folder already exists, name is NOT available
        if (componentDir.exists() && componentDir.isDirectory()) {
            log.info("Component '{}' already exists at path {}", componentName, basePath);
            return false;
        }

        // Otherwise, name is available
        log.info("Component '{}' is available at path {}", componentName, basePath);
        return true;
    }

    /**
     * Fetch all components from local project structure.
     */
    public Map<String, List<String>> getComponentsByGroup(String projectName) {
        String COMPONENTS_PATH = "generated-projects/" + projectName + "/ui.apps/src/main/content/jcr_root/apps/"
                + projectName + "/components";

        Map<String, List<String>> groupedComponents = new HashMap<>();
        scanComponents(new File(COMPONENTS_PATH), groupedComponents, "/apps/" + projectName + "/components");
        return groupedComponents;
    }

    private void scanComponents(File folder, Map<String, List<String>> groupedComponents, String basePath) {
        if (!folder.exists() || !folder.isDirectory())
            return;

        for (File file : folder.listFiles()) {
            if (!file.isDirectory())
                continue;

            String name = file.getName();

            // Skip internal folders
            if (name.startsWith("_cq") || name.equals("new"))
                continue;

            File contentXml = new File(file, ".content.xml");

            if (contentXml.exists() && isComponent(contentXml)) {
                String group = getComponentGroup(contentXml);
                if (group == null)
                    continue; // skip .hidden

                groupedComponents.computeIfAbsent(group, k -> new ArrayList<>());

                // build full relative path like /apps/project/components/form/options
                String relativePath = basePath + "/" + name;

                if (!groupedComponents.get(group).contains(relativePath)) {
                    groupedComponents.get(group).add(relativePath);
                }
            }

            // recurse deeper
            scanComponents(file, groupedComponents, basePath + "/" + name);
        }
    }

    private boolean isComponent(File contentXml) {
        try {
            Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(contentXml);
            Element root = doc.getDocumentElement();

            // Only consider actual components
            return "cq:Component".equals(root.getAttribute("jcr:primaryType"));
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    private String getComponentGroup(File contentXml) {
        try {
            Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(contentXml);
            Element root = doc.getDocumentElement();

            if (root.hasAttribute("componentGroup")) {
                String group = root.getAttribute("componentGroup");
                if (".hidden".equalsIgnoreCase(group)) {
                    return null; // special case: skip hidden
                }
                return group;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return "Others";
    }
    /*
     * 
     * Updating logic below
     */

    /**
     * Search for a component anywhere under the project's components folder
     * Stops at the first match since component names are unique
     * 
     * @param projectName   - AEM project name
     * @param componentName - Exact name of the component to search
     * @return Full path of the component if found, otherwise null
     */
    public String findComponentPathExact(String projectName, String componentName) {
        File componentsRoot = new File(PROJECTS_DIR,
                projectName + "/ui.apps/src/main/content/jcr_root/apps/" + projectName + "/components");

        if (componentsRoot.exists()) {
            return searchComponentRecursiveExact(componentsRoot, componentName);
        }

        return null;
    }

    public String searchComponentRecursiveExact(File dir, String componentName) {
        if (!dir.isDirectory())
            return null;

        // Exact case-sensitive match
        if (dir.getName().equals(componentName)) {
            return dir.getPath();
        }

        // Recurse into subdirectories
        File[] subDirs = dir.listFiles(File::isDirectory);
        if (subDirs != null) {
            for (File subDir : subDirs) {
                String found = searchComponentRecursiveExact(subDir, componentName);
                if (found != null) {
                    return found; // stop as soon as we find it
                }
            }
        }

        return null; // not found in this branch
    }

    @Override
    public Map<String, String> fetchComponentSuperTypes(String projectName) {
        Map<String, String> superTypeMap = new LinkedHashMap<>();

        final String CONTENT_XML = ".content.xml";
        final String SLING_RESOURCE_SUPER_TYPE = "sling:resourceSuperType";

        try {
            Map<String, String> components = fetchComponentsWithGroups(projectName);

            for (String componentName : components.keySet()) {
                String componentPath = findComponentPathExact(projectName, componentName);

                File contentXml = new File(componentPath, CONTENT_XML);
                String superType = null;

                if (contentXml.exists()) {
                    try (InputStream is = new FileInputStream(contentXml)) {
                        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
                        factory.setNamespaceAware(true);
                        DocumentBuilder builder = factory.newDocumentBuilder();
                        Document doc = builder.parse(is);

                        Element root = doc.getDocumentElement();
                        if (root.hasAttribute(SLING_RESOURCE_SUPER_TYPE)) {
                            superType = root.getAttribute(SLING_RESOURCE_SUPER_TYPE);
                        }
                    } catch (Exception e) {
                        log.error("Error parsing .content.xml for component {}", componentName, e);
                    }
                } else {
                    log.warn(".content.xml not found for component {}", componentName);
                }

                // normalize to repo path under /apps
                String normalized = componentPath.replace(File.separatorChar, '/');
                int idx = normalized.indexOf("/apps/");
                String componentRepoPath = (idx != -1) ? normalized.substring(idx) : componentName;

                String compLastName = componentRepoPath.substring(componentRepoPath.lastIndexOf('/') + 1);

                if (superType != null && !superType.isBlank()) {
                    String superLastName = superType.substring(superType.lastIndexOf('/') + 1);

                    // build version-aware label
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
            log.error("Error fetching component supertypes for project {}", projectName, e);
        }

        // sort by value (component label) instead of key (path)
        return superTypeMap.entrySet().stream()
                .sorted(Map.Entry.comparingByValue(String.CASE_INSENSITIVE_ORDER))
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (oldVal, newVal) -> oldVal,
                        LinkedHashMap::new));
    }

    /**
     * Insert into map only if key and value are not already present.
     */
    private void putIfNotExists(Map<String, String> map, String key, String value) {
        if (!map.containsKey(key) && !map.containsValue(value)) {
            map.put(key, value);
        }
    }

    /**
     * Extracts version-aware name from supertype path.
     * Example:
     * core/wcm/components/button/v1/button → button (v1)
     * core/wcm/components/container/v2/container → container (v2)
     * custom/components/teaser → teaser
     */
    private String extractVersionAwareName(String superTypePath) {
        String[] parts = superTypePath.split("/");
        if (parts.length >= 2) {
            String last = parts[parts.length - 1];
            String secondLast = parts[parts.length - 2];

            if (secondLast.matches("v\\d+")) {
                return last + " (" + secondLast + ")";
            }
            return last;
        }
        return superTypePath;
    }

    @Override
    public Map<String, Object> getParentTabs(String projectName, String superType) {
        Map<String, Object> result = new HashMap<>();
        Set<String> tabs = new LinkedHashSet<>(); // preserve order, avoid duplicates

        try {
            collectTabsRecursively(projectName, superType, tabs);

            result.put("hasTabs", !tabs.isEmpty());
            result.put("tabs", new ArrayList<>(tabs));
            log.info("✅ Final merged tabs for {} -> {}", superType, tabs);

        } catch (Exception e) {
            log.error("❌ Error while fetching parent tabs for {}", superType, e);
            result.put("hasTabs", false);
            result.put("tabs", new ArrayList<>());
        }

        return result;
    }

    /**
     * Recursively collects tabs from current component and its superTypes.
     */
    private void collectTabsRecursively(String projectName, String superType, Set<String> tabs) throws Exception {
        boolean isCore = superType.startsWith("core/");
        String basePath = System.getProperty("user.dir") +
                (isCore
                        ? "/src/main/resources/" + superType
                        : "/generated-projects/" + projectName + "/ui.apps/src/main/content/jcr_root" + superType);

        // Step 1: parse dialog
        File dialogFile = new File(basePath + "/_cq_dialog/.content.xml");
        if (dialogFile.exists()) {
            List<String> currentTabs = isCore
                    ? parseCoreTabsFromDialog(dialogFile)
                    : parseProjectTabsFromDialog(dialogFile);
            tabs.addAll(currentTabs);
            log.info("➡️ Tabs collected from {}: {}", superType, currentTabs);
        }

        // Step 2: check superType in .content.xml
        File compContentFile = new File(basePath + "/.content.xml");
        if (compContentFile.exists()) {
            log.info("componentFile for supertype,{}", compContentFile);
            String parentSuperType = readSuperType(compContentFile);
            log.info("parent,{}", parentSuperType);
            if (parentSuperType != null && !parentSuperType.isEmpty()) {
                if (parentSuperType.startsWith("core/")) {
                    // Only collect core tabs once
                    String corePath = System.getProperty("user.dir") + "/src/main/resources/" + parentSuperType;
                    File coreDialog = new File(corePath + "/_cq_dialog/.content.xml");
                    if (coreDialog.exists()) {
                        List<String> coreTabs = parseCoreTabsFromDialog(coreDialog);
                        tabs.addAll(coreTabs);
                        log.info("➡️ Core Tabs collected from {}: {}", parentSuperType, coreTabs);
                    } else {
                        log.warn("⚠️ Core dialog not found at {}", coreDialog.getAbsolutePath());
                    }
                } else {
                    // Recurse for project parent
                    collectTabsRecursively(projectName, parentSuperType, tabs);
                }
            }
        }
    }

    /**
     * Parse dialog file and extract tab names for project (normal) components.
     */
    private List<String> parseProjectTabsFromDialog(File dialogFile) throws Exception {
        List<String> tabs = new ArrayList<>();
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document doc = builder.parse(dialogFile);

        NodeList nodes = doc.getElementsByTagName("*");
        for (int i = 0; i < nodes.getLength(); i++) {
            org.w3c.dom.Node node = nodes.item(i);
            NamedNodeMap attrs = node.getAttributes();
            if (attrs == null)
                continue;

            org.w3c.dom.Node resType = attrs.getNamedItem("sling:resourceType");
            if (resType != null && "granite/ui/components/coral/foundation/tabs".equals(resType.getNodeValue())) {
                NodeList itemsNodes = node.getChildNodes();
                for (int j = 0; j < itemsNodes.getLength(); j++) {
                    org.w3c.dom.Node itemsNode = itemsNodes.item(j);
                    if (!"items".equals(itemsNode.getNodeName()))
                        continue;

                    NodeList tabNodes = itemsNode.getChildNodes();
                    for (int k = 0; k < tabNodes.getLength(); k++) {
                        org.w3c.dom.Node tabNode = tabNodes.item(k);
                        if (tabNode.getNodeType() != org.w3c.dom.Node.ELEMENT_NODE)
                            continue;

                        NamedNodeMap tabAttrs = tabNode.getAttributes();
                        if (tabAttrs == null)
                            continue;

                        org.w3c.dom.Node tabResType = tabAttrs.getNamedItem("sling:resourceType");
                        if (tabResType != null && "granite/ui/components/coral/foundation/container"
                                .equals(tabResType.getNodeValue())) {
                            String tabTitle = tabAttrs.getNamedItem("jcr:title") != null
                                    ? tabAttrs.getNamedItem("jcr:title").getNodeValue()
                                    : tabNode.getNodeName();
                            tabs.add(tabTitle);
                            log.info("   ➕ Project Tab detected: {}", tabTitle);
                        }
                    }
                }
            }
        }
        return tabs;
    }

    /**
     * Parse dialog file and extract tab names for Core components.
     * Stops at first <tabs> found.
     */
    private List<String> parseCoreTabsFromDialog(File dialogFile) throws Exception {
        List<String> tabs = new ArrayList<>();
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document doc = builder.parse(dialogFile);

        org.w3c.dom.Node root = doc.getDocumentElement();
        parseTabsRecursive(root, tabs);
        return tabs;
    }

    private void parseTabsRecursive(org.w3c.dom.Node node, List<String> tabs) {
        if (node.getNodeType() != org.w3c.dom.Node.ELEMENT_NODE)
            return;

        NamedNodeMap attrs = node.getAttributes();

        // Case 1: Node is <tabs>
        if ("tabs".equals(node.getNodeName()) ||
                (attrs != null && attrs.getNamedItem("sling:resourceType") != null &&
                        "granite/ui/components/coral/foundation/tabs"
                                .equals(attrs.getNamedItem("sling:resourceType").getNodeValue()))) {

            NodeList itemsNodes = node.getChildNodes();
            for (int i = 0; i < itemsNodes.getLength(); i++) {
                org.w3c.dom.Node itemsNode = itemsNodes.item(i);
                if (!"items".equals(itemsNode.getNodeName()))
                    continue;

                NodeList tabNodes = itemsNode.getChildNodes();
                for (int j = 0; j < tabNodes.getLength(); j++) {
                    org.w3c.dom.Node tabNode = tabNodes.item(j);
                    if (tabNode.getNodeType() != org.w3c.dom.Node.ELEMENT_NODE)
                        continue;

                    NamedNodeMap tabAttrs = tabNode.getAttributes();
                    if (tabAttrs == null)
                        continue;

                    org.w3c.dom.Node resTypeAttr = tabAttrs.getNamedItem("sling:resourceType");
                    if (resTypeAttr != null &&
                            "granite/ui/components/coral/foundation/container".equals(resTypeAttr.getNodeValue())) {

                        org.w3c.dom.Node titleAttr = tabAttrs.getNamedItem("jcr:title");
                        if (titleAttr != null) {
                            tabs.add(titleAttr.getNodeValue());
                            log.info("   ➕ Core Tab detected: {}", titleAttr.getNodeValue());
                        }
                    }

                    // Recurse into nested <tabs> inside this tab node
                    parseTabsRecursive(tabNode, tabs);
                }
            }
        } else {
            // Recurse into child nodes
            NodeList children = node.getChildNodes();
            for (int i = 0; i < children.getLength(); i++) {
                parseTabsRecursive(children.item(i), tabs);
            }
        }
    }

    /**
     * Reads sling:resourceSuperType from .content.xml.
     */
    private String readSuperType(File compContentFile) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true); // important
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
                    if ("sling:resourceSuperType".equals(name) || name.endsWith(":resourceSuperType")) {
                        log.info("Super Type....,{}", attr.getNodeValue());
                        return attr.getNodeValue();
                    }
                }
            }
        }
        return null;
    }
}
