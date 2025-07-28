 package com.aem.builder.service.impl;

import com.aem.builder.model.TemplateModel;
import com.aem.builder.service.TemplateService;

import com.aem.builder.util.TemplateUtil;
import org.apache.commons.io.FileUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;


@Service
public class TemplateServiceImpl implements TemplateService {
    @Autowired
    private ResourceLoader resourceLoader;
    @Override
    public List<String> getTemplateFileNames() throws IOException {
        Resource resource = resourceLoader.getResource("classpath:aem-templates");
        List<String> templateNames = new ArrayList<>();
        // Using ClassLoader to list files
        try (var inputStream = getClass().getClassLoader().getResourceAsStream("aem-templates")) {
            if (inputStream != null) {
                var fileNames = new java.io.File(getClass().getClassLoader().getResource("aem-templates").getFile())
                        .listFiles();
                if (fileNames != null) {
                    for (var file : fileNames) {
                        templateNames.add(file.getName());
                    }
                }
            }
        }

        return templateNames;
    }
    @Override
    public void copySelectedTemplatesToGeneratedProject(String projectName, List<String> selectedTemplates) throws IOException {
        // 1. Define destination
        String destinationPath = "generated-projects/" + projectName +
                "/ui.content/src/main/content/jcr_root/conf/" + projectName + "/settings/wcm/templates";
        File destinationFolder = new File(destinationPath);
        if (!destinationFolder.exists()) {
            destinationFolder.mkdirs(); // create nested folders
        }
        for (String templateName : selectedTemplates) {
            // Load template from classpath
            File resource = new File("src/main/resources/aem-templates/" + templateName);
            File targetFile = new File(destinationFolder, templateName);
            FileUtils.copyDirectory(resource, targetFile);
        }
    }
    @Override
    public List<String> getDistinctTemplates(String projectname, List<String> resourceTemplates,List<String>projectTemplates) {
        List<String>distinct=new ArrayList<>();
        distinct= resourceTemplates.stream()
                .filter(t -> !projectTemplates.contains(t))
                .collect(Collectors.toList());
        return distinct;
    }
    @Override
    public List<String>getCommonTemplates(List<String> resourceTemplates,List<String>projectTemplates){
        List<String> commonTemplates = resourceTemplates.stream()
                .filter(projectTemplates::contains)
                .collect(Collectors.toList());

        System.out.println("commom "+commonTemplates);
        return commonTemplates;

    }
    @Override
    public List<String> getTemplateNamesFromDestination(String projectName) {
        List<String> templateNames = new ArrayList<>();
        String destinationPath = "generated-projects/" + projectName +
                "/ui.content/src/main/content/jcr_root/conf/" + projectName + "/settings/wcm/templates";
        File folder = new File(destinationPath);
        if (folder.exists() && folder.isDirectory()) {
            File[] files = folder.listFiles();
            if (files != null) {
                for (File file : files) {
                    String filename=file.getName();
                    if(!filename.endsWith(".xml")) {
                        templateNames.add(filename);
                    }
                }
            }
        }
        return templateNames;
    }



    private static final String PROJECTS_DIR = "generated-projects";

    @Override
    public List<String> fetchTemplatesFromGeneratedProjects(String projectName) {
        File templatesDir = new File(PROJECTS_DIR, projectName + "/ui.content/src/main/content/jcr_root/conf/" + projectName + "/settings/wcm/templates");
        if (templatesDir.exists()) {
            return Arrays.stream(templatesDir.listFiles(File::isDirectory))
                    .map(File::getName)
                    .collect(Collectors.toList());
        }
        return List.of();
    }

    //creating templates



    @Override
    public void createTemplate(TemplateModel model, String projectname) throws IOException {

        System.out.println(model.getTitle());
        System.out.println(model.getName());
        System.out.println(model.getStatus());
        String url = "generated-projects/" + projectname + "/ui.content/src/main/content/jcr_root/conf/" + projectname + "/settings/wcm/templates/" + model.getName();
        String targetpath = url;

        // Create parent directory
        new File(url).mkdirs();
        System.out.println("Created directory: " + url);

        // Create subfolders
        new File(targetpath + "/jcr:content").mkdirs();
        new File(targetpath + "/initial").mkdirs();
        new File(targetpath + "/structure").mkdirs();
        new File(targetpath+"/policies").mkdirs();

        // Write XML files
        writeFile(targetpath + "/jcr:content/.content.xml", TemplateUtil.getJcrContentXmlPage(model.getName(), projectname,model.getStatus(), model.getTemplateType())); // Pass projectname here
        writeFile(targetpath + "/.content.xml", TemplateUtil.getTemplateRootXmlPage(model.getName()));
        writeFile(targetpath + "/initial/.content.xml", TemplateUtil.getInitialXmlPage(projectname,model.getName()));
        writeFile(targetpath + "/structure/.content.xml", TemplateUtil.getStructureXmlPage(model.getName(),projectname)); // Pass projectname here
        writeFile(targetpath+"/policies/.content.xml",TemplateUtil.getPoliciesPage(projectname));
    }

    void writeFile(String path, String content) throws IOException {
        Path filePath = Paths.get(path);
        Files.createDirectories(filePath.getParent());
        Files.write(filePath, content.getBytes(StandardCharsets.UTF_8));
        System.out.println("Written file: " + path);
    }

    //template xf type
    @Override
    public void createTemplateXf(TemplateModel model, String projectname) throws IOException {

        System.out.println(model.getTitle());
        System.out.println(model.getName());
        System.out.println(model.getStatus());
        String url = "generated-projects/" + projectname + "/ui.content/src/main/content/jcr_root/conf/" + projectname + "/settings/wcm/templates/" + model.getName();
        String targetpath = url;

        // Create parent directory
        new File(url).mkdirs();
        System.out.println("Created directory: " + url);

        // Create subfolders
        new File(targetpath + "/jcr:content").mkdirs();
        new File(targetpath + "/initial").mkdirs();
        new File(targetpath + "/structure").mkdirs();
        new File(targetpath+"/policies").mkdirs();

        // Write XML files
        // Write XML files
        writeFile(targetpath + "/jcr:content/.content.xml", TemplateUtil.getJcrContentXmlPage(model.getName(), projectname,model.getStatus(), model.getTemplateType())); // Pass projectname here
        writeFile(targetpath + "/.content.xml", TemplateUtil.getTemplateRootXmlPage(model.getName()));
        writeFile(targetpath + "/initial/.content.xml", TemplateUtil.getInitialXmlPage(projectname,model.getName()));
        writeFile(targetpath + "/structure/.content.xml", TemplateUtil.generateStructureContentXmlXf(projectname,model.getName())); // Pass projectname here
        writeFile(targetpath+"/policies/.content.xml",TemplateUtil.generatePoliciesXmlXf(projectname));
    }




}