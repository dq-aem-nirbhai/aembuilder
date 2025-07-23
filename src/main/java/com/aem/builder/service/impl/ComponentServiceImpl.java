package com.aem.builder.service.impl;

import com.aem.builder.service.ComponentService;
import lombok.RequiredArgsConstructor;
import org.apache.commons.io.FileUtils;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ComponentServiceImpl implements ComponentService {

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
    public Map<String, List<String>> getProjectComponentsMap(List<String> projects) {
        Map<String, List<String>> projectComponentsMap = new HashMap<>();

        for (String project : projects) {
            String componentDirPath = System.getProperty("user.dir") +
                    "/generated-projects/" + project +
                    "/ui.apps/src/main/content/jcr_root/apps/" + project + "/components/content";

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
                    "/ui.apps/src/main/content/jcr_root/apps/" + projectName + "/components/content";

            File content = new File(contentFolderPath);
            if (!content.exists()) {
                boolean created = content.mkdirs();
                if (!created) {
                    System.err.println("Failed to create content folder for components.");
                    return;
                }
            }

            copySelectedComponents(selectedComponents, contentFolderPath);

            System.out.println("✅ Selected components copied to content folder in project: " + projectName);
        } catch (Exception e) {
            System.err.println("❌ Error while adding components to project.");
            e.printStackTrace();
        }
    }

    @Override
    public void copySelectedComponents(List<String> selectedComponents, String targetPath) {
        if (selectedComponents == null || selectedComponents.isEmpty()) return;

        try {
            for (String component : selectedComponents) {
                File source = new File("src/main/resources/aem-components/" + component);
                File destination = new File(targetPath + "/" + source.getName());

                if (!source.exists()) {
                    System.err.println("⚠️ Source component not found: " + source.getAbsolutePath());
                    continue;
                }

                if (destination.exists()) {
                    FileUtils.deleteDirectory(destination);
                }

                FileUtils.copyDirectory(source, destination);
                System.out.println("Copied component: " + component + " → " + destination.getAbsolutePath());
            }
        } catch (IOException e) {
            System.err.println("❌ Failed to copy selected components.");
            e.printStackTrace();
        }
    }
}