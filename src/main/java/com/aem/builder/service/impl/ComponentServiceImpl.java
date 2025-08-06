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
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import lombok.RequiredArgsConstructor;
import org.apache.commons.io.FileUtils;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
@Slf4j
public class ComponentServiceImpl implements ComponentService {

    private static final String PROJECTS_DIR = "generated-projects";

    @Override
    public List<String> fetchComponentsFromGeneratedProjects(String projectName) {
        File componentsDir = new File(PROJECTS_DIR,
                projectName + "/ui.apps/src/main/content/jcr_root/apps/" + projectName + "/components");
        if (componentsDir.exists()) {
            return Arrays.stream(componentsDir.listFiles(File::isDirectory))
                    .map(File::getName)
                    .collect(Collectors.toList());
        }
        return List.of();
    }

    @Override
    public Map<String, String> fetchComponentsWithGroups(String projectName) {
        Map<String, String> result = new LinkedHashMap<>();
        File componentsDir = new File(PROJECTS_DIR,
                projectName + "/ui.apps/src/main/content/jcr_root/apps/" + projectName + "/components");
        if (componentsDir.exists()) {
            File[] dirs = componentsDir.listFiles(File::isDirectory);
            if (dirs != null) {
                for (File comp : dirs) {
                    File contentXml = new File(comp, ".content.xml");
                    String group = "";
                    if (contentXml.exists()) {
                        String content = FileGenerationUtil.readFile(contentXml);
                        if (content.contains("componentGroup")) {
                            int idx = content.indexOf("componentGroup");
                            int start = content.indexOf("\"", idx) + 1;
                            int end = content.indexOf("\"", start);
                            group = content.substring(start, end);
                        }
                    }
                    result.put(comp.getName(), group);
                }
            }
        }
        return result;
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
    public List<String>getExistingProjects() {
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
        String basePath = PROJECTS_DIR + "/" + projectName + "/ui.apps/src/main/content/jcr_root/apps/" + projectName
                + "/components/" + componentName;
        String group = "";
        String superType = null;
        List<ComponentField> fields = new ArrayList<>();

        try {
            File contentXml = new File(basePath, ".content.xml");
            if (contentXml.exists()) {
                String content = FileGenerationUtil.readFile(contentXml);
                if (content.contains("componentGroup")) {
                    int idx = content.indexOf("componentGroup");
                    int start = content.indexOf("\"", idx) + 1;
                    int end = content.indexOf("\"", start);
                    group = content.substring(start, end);
                }
                if (content.contains("sling:resourceSuperType")) {
                    int idx = content.indexOf("sling:resourceSuperType");
                    int start = content.indexOf("\"", idx) + 1;
                    int end = content.indexOf("\"", start);
                    superType = content.substring(start, end);
                }
            }

            File dialogXml = new File(basePath + "/_cq_dialog/.content.xml");
            if (dialogXml.exists()) {
                DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
                factory.setNamespaceAware(false);
                DocumentBuilder builder = factory.newDocumentBuilder();
                Document doc = builder.parse(dialogXml);

                Element root = doc.getDocumentElement();
                Element content = (Element) root.getElementsByTagName("content").item(0);
                if (content != null) {
                    Element items = (Element) content.getElementsByTagName("items").item(0);
                    if (items != null) {
                        Element tabs = (Element) items.getElementsByTagName("tabs").item(0);
                        if (tabs != null) {
                            Element tabsItems = (Element) tabs.getElementsByTagName("items").item(0);
                            if (tabsItems != null) {
                                NodeList tabNodes = tabsItems.getChildNodes();
                                for (int t = 0; t < tabNodes.getLength(); t++) {
                                    Node tabNode = tabNodes.item(t);
                                    if (tabNode instanceof Element tabElement) {
                                        Element tabItems = (Element) tabElement.getElementsByTagName("items").item(0);
                                        if (tabItems != null) {
                                            NodeList fieldNodes = tabItems.getChildNodes();
                                            for (int i = 0; i < fieldNodes.getLength(); i++) {
                                                Node node = fieldNodes.item(i);
                                                if (node instanceof Element elem) {
                                                    fields.add(parseField(elem));
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
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
    public Map<String, String> loadComponentSources(String projectName, String componentName) {
        Map<String, String> sources = new HashMap<>();
        Path htmlPath = Paths.get(PROJECTS_DIR, projectName,
                "ui.apps/src/main/content/jcr_root/apps", projectName,
                "components", componentName, componentName + ".html");
        if (Files.exists(htmlPath)) {
            try {
                sources.put("html", FileUtils.readFileToString(htmlPath.toFile(), StandardCharsets.UTF_8));
            } catch (IOException e) {
                log.error("Failed to read component html for {}", componentName, e);
            }
        }

        Path javaRoot = Paths.get(PROJECTS_DIR, projectName, "core/src/main/java");
        String modelFile = capitalize(componentName) + "Model.java";
        try (Stream<Path> paths = Files.walk(javaRoot)) {
            paths.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().equals(modelFile))
                    .findFirst()
                    .ifPresent(p -> {
                        try {
                            sources.put("java", FileUtils.readFileToString(p.toFile(), StandardCharsets.UTF_8));
                        } catch (IOException e) {
                            log.error("Failed to read component model for {}", componentName, e);
                        }
                    });
        } catch (IOException e) {
            log.error("Failed to locate model file for {}", componentName, e);
        }
        return sources;
    }

    private String capitalize(String input) {
        return (input == null || input.isEmpty()) ? input
                : input.substring(0, 1).toUpperCase() + input.substring(1);
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

    private String getFieldTypeFromResource(String resourceType) {
        return FieldType.getTypeResourceMap().entrySet().stream()
                .filter(e -> e.getValue().equals(resourceType))
                .map(Map.Entry::getKey)
                .findFirst()
                .orElse("");
    }

    private ComponentField parseField(Element elem) {
        String fieldLabel = elem.getAttribute("fieldLabel");
        String nameAttr = elem.getAttribute("name");
        String fieldName = nameAttr.startsWith("./") ? nameAttr.substring(2) : nameAttr;
        String resourceType = elem.getAttribute("sling:resourceType");
        String fieldType = getFieldTypeFromResource(resourceType);

        List<OptionItem> options = null;
        List<ComponentField> nested = null;

        if ("multifield".equals(fieldType)) {
            Element fieldElem = (Element) elem.getElementsByTagName("field").item(0);
            if (fieldElem != null) {
                Element items = (Element) fieldElem.getElementsByTagName("items").item(0);
                if (items != null) {
                    nested = new ArrayList<>();
                    NodeList nodes = items.getChildNodes();
                    for (int i = 0; i < nodes.getLength(); i++) {
                        Node n = nodes.item(i);
                        if (n instanceof Element child) {
                            nested.add(parseField(child));
                        }
                    }
                }
            }
        } else if (fieldType.equals("select") || fieldType.equals("multiselect")
                || fieldType.equals("checkboxgroup") || fieldType.equals("radiogroup")) {
            Element items = (Element) elem.getElementsByTagName("items").item(0);
            if (items != null) {
                options = new ArrayList<>();
                NodeList nodes = items.getChildNodes();
                for (int i = 0; i < nodes.getLength(); i++) {
                    Node n = nodes.item(i);
                    if (n instanceof Element opt) {
                        options.add(new OptionItem(opt.getAttribute("text"), opt.getAttribute("value")));
                    }
                }
            }
        }

        return new ComponentField(fieldName, fieldLabel, fieldType, nested, options);
    }

    @Override
    public Map<String, List<String>> getProjectComponentsMap(List<String> projects) {
        Map<String, List<String>> projectComponentsMap = new HashMap<>();

        for (String project : projects) {
            String componentDirPath = System.getProperty("user.dir") +
                    "/generated-projects/" + project +
                    "/ui.apps/src/main/content/jcr_root/apps/" + project + "/components";

            File componentDir = new File(componentDirPath);

            if (componentDir.exists() && componentDir.isDirectory()) {
                File[] componentDirs = componentDir.listFiles(File::isDirectory);
                List<String> componentNames = new ArrayList<>();
                if (componentDirs != null) {
                    for (File comp : componentDirs) {
                        componentNames.add(comp.getName());
                    }
                }
                projectComponentsMap.put(project, componentNames);
            } else {
                projectComponentsMap.put(project, new ArrayList<>());
            }


        }

        return projectComponentsMap;
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
        log.info("ModelPath{}",modelPath);

        // Get full model base path
        String modelBasePath = modelPath.toString();

        log.info("ModelBasePath{}",modelBasePath);

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
                    //String fqcn = extractFullyQualifiedClassName(parentModel, "com." + projectName + ".core.models");
                    String fqcn = extractFullyQualifiedClassName(parentModel, packageName);

                    if (fqcn != null) {
                        htmlContent = htmlContent.replaceAll("data-sly-use\\.model=\"[^\"]+\"",
                                "data-sly-use.model=\"" + fqcn + "\"");
                        FileUtils.writeStringToFile(html, htmlContent, "UTF-8");
                    }
                }

                // Copy model and its dependencies
                if (parentModel != null && parentModel.exists()) {
                   copyModelAndDependencies(parentModel, slingModelsSourcePath, modelBasePath,packageName, copiedModels);

                } else {
                    System.out.println("No matching Sling Model found for: " + component);
                }
            } catch (IOException e) {
                System.err.println("Failed to process component: " + component);
                e.printStackTrace();
            }
        }
    }

    private static Path findModelBasePath(Path javaSourceRoot)  {
        try (Stream<Path> paths = Files.walk(javaSourceRoot)) {
            Optional<Path> modelPath = paths
                    .filter(Files::isDirectory)
                    .filter(p -> p.getFileName().toString().equals("models"))
                    .findFirst();

            return modelPath.orElseThrow(() ->
                    new IOException("models directory not found under: " + javaSourceRoot));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private File findMatchingModelFile(String modelsDirPath, String componentName) {
        File dir = new File(modelsDirPath);
        if (!dir.exists() || !dir.isDirectory()) return null;

        File[] files = dir.listFiles((d, name) -> name.endsWith(".java"));
        if (files == null) return null;

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

    private void copyModelAndDependencies(File modelFile, String sourceBase, String targetBase,String targetPackageName,  Set<String> copiedModels) throws IOException {
        if (modelFile == null || !modelFile.exists()) return;

        String modelName = modelFile.getName();
        if (copiedModels.contains(modelName)) return;

        String originalContent = FileUtils.readFileToString(modelFile, "UTF-8");

        // Update package declaration
       // String content = originalContent.replace("package com.aem.builder.slingModels;", "package com." + projectName + ".core.models;");

        /*String content = originalContent.replaceAll(
                "package\\s+com\\.aem\\.builder\\.[\\w.]+;",
                "package com." + projectName + ".core.models;"
        );*/

        String content = originalContent.replaceFirst(
                "package\\s+com\\.aem\\.builder\\.[\\w.]+;",
                "package " + targetPackageName + ";"
        );


        /*// Update import statements for internal model classes
        Pattern importPattern = Pattern.compile("import\\s+com\\.aem\\.builder\\.slingModels\\.(\\w+);");
        Matcher importMatcher = importPattern.matcher(content);
        StringBuffer updatedContent = new StringBuffer();
        while (importMatcher.find()) {
            String className = importMatcher.group(1);
            String newImport = "import com." + projectName + ".core.models." + className + ";";
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
                copyModelAndDependencies(depFile, sourceBase, targetBase, projectName, copiedModels);
            }
        }*/

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
        } catch(IOException e){
            System.err.println("Failed to extract FQCN from model file.");
            e.printStackTrace();
        }
        return null;
    }


    //component creation
    @Override
    public List<String> getComponentGroups(String projectName) {
        String path = PROJECTS_DIR + "/" + projectName + "/ui.apps/src/main/content/jcr_root/apps/" + projectName + "/components";
        File folder = new File(path);
        Set<String> groups = new HashSet<>();
        groups.add(projectName);
        if (folder.exists()) {
            File[] subDirs = folder.listFiles(File::isDirectory);
            if (subDirs != null) {
                for (File comp : subDirs) {
                    File contentXml = new File(comp, ".content.xml");
                    if (contentXml.exists()) {
                        String content = FileGenerationUtil.readFile(contentXml);
                        if (content.contains("componentGroup")) {
                            int idx = content.indexOf("componentGroup");
                            int start = content.indexOf("\"", idx) + 1;
                            int end = content.indexOf("\"", start);
                            groups.add(content.substring(start, end));
                        }
                    }
                }
            }
        }
        groups.removeIf(g -> g.equals(projectName + " - Content")
                || g.equals(projectName + " - Structure")
                || g.equals(".hidden"));
        return groups.isEmpty() ? List.of(projectName) : new ArrayList<>(groups);
    }

    @Override
    public void generateComponent(String projectName, ComponentRequest request) {
        FileGenerationUtil.generateAllFiles(projectName, request);
    }


    //component checking
    @Override
    public boolean isComponentNameAvailable(String projectName, String componentName) {
        // Folder where all components are stored
        String basePath = "generated-projects/" + projectName + "/ui.apps/src/main/content/jcr_root/apps/" + projectName + "/components";
        File componentsDir = new File(basePath);

        if (!componentsDir.exists() || !componentsDir.isDirectory()) {
            // If the parent folder doesn't exist yet, name is available
            log.warn("Components folder does not exist: {}", basePath);
            return true;
        }

        String[] existingComponents = componentsDir.list();
        if (existingComponents != null) {
            for (String name : existingComponents) {
                if (name.equals(componentName)) { // 🔍 Case-sensitive match
                    log.info("Component '{}' already exists (case-sensitive match)", name);
                    return false; // Not available
                }
            }
        }

        return true; // Available if no exact case-sensitive match found
    }
}
