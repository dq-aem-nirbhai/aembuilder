package com.aem.builder.service.impl;

import com.aem.builder.model.DTO.ComponentRequest;
import com.aem.builder.service.ComponentService;

import com.aem.builder.util.FileGenerationUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.*;
import java.util.stream.Collectors;
import com.aem.builder.service.ComponentService;

import lombok.RequiredArgsConstructor;
import org.apache.commons.io.FileUtils;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.util.List;

import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ComponentServiceImpl implements ComponentService {

    private static final String PROJECTS_DIR = "generated-projects";

    /**
     * Fetches the component folder names from the generated project structure.
     */
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

    /**
     * Lists all available component names from the /aem-components folder.
     */
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

    /**
     * Returns the list of components that are common between all available and the project-specific ones.
     */

    @Override
    public List<String> getCommonComponents(List<String> allComponents, List<String> projectComponents) {
        return allComponents.stream()
                .filter(projectComponents::contains)
                .collect(Collectors.toList());
    }

    /**
     * Returns components that exist in the full list but not in the project.
     */
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

    /**
     * Builds a map of project names to their respective component list.
     */
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

    /**
     * Adds selected components into the existing generated project:
     * - Copies the component folder
     * - Replaces resource type
     * - Updates data-sly-use model paths
     * - Copies associated Sling Models recursively
     */
    @Override
    public void addComponentsToExistingProject(String projectName, List<String> selectedComponents) {
        try {
            String baseDir = System.getProperty("user.dir") + "/generated-projects/";
            String contentFolderPath = baseDir + projectName +
                    "/ui.apps/src/main/content/jcr_root/apps/" + projectName + "/components";

            copySelectedComponents(selectedComponents, contentFolderPath);

            System.out.println(" Selected components copied to content folder in project: " + projectName);

        } catch (Exception e) {
            System.err.println("Error while adding components to project.");
            e.printStackTrace();
        }
    }

    /**
     * Handles the actual file copy and transformation for selected components:
     * - Updates .content.xml with correct sling:resourceType
     * - Updates HTML to use correct model path
     * - Copies related Sling Models recursively
     */

        @Override
        public void copySelectedComponents(List<String> selectedComponents, String targetPath) {
            if (selectedComponents == null || selectedComponents.isEmpty())
                return;

            try {
                for(String component : selectedComponents) {
                    File source = new File("src/main/resources/aem-components/" + component);
                    File destination = new File(targetPath + "/" + source.getName());

                    if (!source.exists()) {
                        System.err.println("Source component not found: " + source.getAbsolutePath());
                        continue;
                    }

                    if (destination.exists()) {
                        FileUtils.deleteDirectory(destination);
                    }

                    FileUtils.copyDirectory(source, destination);
                    System.out.println("Copied component: " + component + " → " + destination.getAbsolutePath());
                }
            } catch (IOException e) {
        System.err.println(" Failed to copy selected components.");
            e.printStackTrace();
        }
        }

//component creation
@Override
public List<String> getComponentGroups(String projectName) {
    String path = PROJECTS_DIR + "/" + projectName + "/ui.apps/src/main/content/jcr_root/apps/" + projectName + "/components";
    File folder = new File(path);
    Set<String> groups = new HashSet<>();
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
}}
