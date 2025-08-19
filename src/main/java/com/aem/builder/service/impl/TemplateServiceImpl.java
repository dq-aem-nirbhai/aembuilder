package com.aem.builder.service.impl;

import com.aem.builder.model.TemplateModel;
import com.aem.builder.service.TemplateService;

import com.aem.builder.util.TemplateUtil;
import org.apache.commons.io.FileUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.*;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;


@Service
public class TemplateServiceImpl implements TemplateService {
    List<TemplateModel>templateModels;
    @Autowired
    private ResourceLoader resourceLoader;
    @Override
    public List<String> getTemplateFileNames() throws IOException {
        Resource resource = resourceLoader.getResource("classpath:aem-templates");
        List<String> templateNames = new ArrayList<>();
        // Using ClassLoader to list files
        try (var inputStream = getClass().getClassLoader().getResourceAsStream("aem-templates")) {
            if (inputStream != null) {
                var fileNames =
                        new java.io.File(getClass().getClassLoader().getResource("aem-templates").getFile())
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
    public void copySelectedTemplatesToGeneratedProject(String projectName, List<String> selectedTemplates)
            throws IOException {
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
    public List<String> getDistinctTemplates(String projectname,
                                             List<String> resourceTemplates,List<String>projectTemplates) {
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
    @Override
    public List<String> getTemplateTypesFromDestination(String projectName) {
        List<String> templateNames = new ArrayList<>();
        String destinationPath = "generated-projects/" + projectName +
                "/ui.content/src/main/content/jcr_root/conf/" + projectName + "/settings/wcm/template-types";
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
        File templatesDir = new File(PROJECTS_DIR, projectName +
                "/ui.content/src/main/content/jcr_root/conf/" + projectName + "/settings/wcm/templates");
        if (templatesDir.exists()) {
            return Arrays.stream(templatesDir.listFiles(File::isDirectory))
                    .map(File::getName)
                    .collect(Collectors.toList());
        }
        return List.of();
    }


    //creating templates



    @Override
    public TemplateModel createTemplate(TemplateModel model, String projectname) throws IOException {




        String url = "generated-projects/" + projectname + "/ui.content/src/main/content/jcr_root/conf/"
                + projectname + "/settings/wcm/templates/" + model.getTitle();
        String targetpath = url;

        // Create parent directory
        new File(url).mkdirs();


        // Create subfolders
        //   new File(targetpath + "/jcr:content").mkdirs();
        new File(targetpath + "/initial").mkdirs();
        new File(targetpath + "/structure").mkdirs();
        new File(targetpath+"/policies").mkdirs();

        // Write XML files
        writeFile(targetpath + "/.content.xml", TemplateUtil.getTemplateRootXmlPage(model.getTitle(),projectname,model.getTemplateType(),model.getStatus(),model.getDescription()));

        if(model.getTemplateType().equals("page")) {
            writeFile(targetpath + "/initial/.content.xml", TemplateUtil.getInitialXmlPage(projectname,
                    model.getTitle()));
            writeFile(targetpath + "/structure/.content.xml", TemplateUtil.getStructureXmlPage(model.getTitle(),
                    projectname)); // Pass projectname here
            writeFile(targetpath + "/policies/.content.xml", TemplateUtil.getPoliciesPage(projectname));

        }
        else {

            writeFile(targetpath + "/initial/.content.xml", TemplateUtil.getIntialContentXf(projectname,
                    model.getTitle()));
            //getIntialContentXf

            writeFile(targetpath + "/structure/.content.xml", TemplateUtil.generateStructureContentXmlXf(projectname,
                    model.getTitle())); // Pass projectname here
            writeFile(targetpath+"/policies/.content.xml",TemplateUtil.generatePoliciesXmlXf(projectname));

        }

        File xmlFile = new File("generated-projects/" + projectname + "/ui.content/src/main/content/jcr_root/conf/"
                + projectname + "/settings/wcm/templates/" +"/.content.xml");
        DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
        DocumentBuilder dBuilder = null;
        try {
            dBuilder = dbFactory.newDocumentBuilder();
        } catch (ParserConfigurationException e) {
            throw new RuntimeException(e);
        }
        Document doc = null;
        try {
            doc = dBuilder.parse(xmlFile);
        } catch (SAXException e) {
            throw new RuntimeException(e);
        }

        Element root = doc.getDocumentElement();

// Check if node already exists
        String newTemplateName = model.getTitle(); // from user
        if (doc.getElementsByTagName(newTemplateName).getLength() == 0) {
            Element newElement = doc.createElement(newTemplateName);
            root.appendChild(newElement);

            // Write back to file
            Transformer transformer = null;
            try {
                transformer = TransformerFactory.newInstance().newTransformer();
            } catch (TransformerConfigurationException e) {
                throw new RuntimeException(e);
            }

            try {
                transformer.transform(new DOMSource(doc), new StreamResult(xmlFile));
            } catch (TransformerException e) {
                throw new RuntimeException(e);
            }
        }



        return model;
    }



    void writeFile(String path, String content) throws IOException {
        Path filePath = Paths.get(path);
        Files.createDirectories(filePath.getParent());
        Files.write(filePath, content.getBytes(StandardCharsets.UTF_8));

    }



    @Override
    public TemplateModel loadTemplateByName(String projectName, String templateName) {
        try {
            String templateFolderPath = "generated-projects/" + projectName +
                    "/ui.content/src/main/content/jcr_root/conf/" + projectName +
                    "/settings/wcm/templates/" + templateName;

            File contentXmlFile = new File(templateFolderPath + "/.content.xml");
            if (!contentXmlFile.exists()) {
                throw new FileNotFoundException(".content.xml not found for template: " + templateName);
            }

            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(contentXmlFile);
            Element root = doc.getDocumentElement();

            TemplateModel model = new TemplateModel();
            model.setTitle(root.getAttribute("jcr:title"));

            Element content = (Element) root.getElementsByTagName("jcr:content").item(0);
            model.setTitle(content.getAttribute("jcr:title"));
            model.setStatus(content.getAttribute("status"));
            model.setDescription(content.getAttribute("jcr:description"));
            String templateType=content.getAttribute("cq:templateType");


            if (templateType != null && templateType.contains("/")) {
                model.setTemplateType(templateType.substring(templateType.lastIndexOf("/") + 1));

            }

            return model;

        } catch (Exception e) {
            e.printStackTrace();
            return null; // <- fallback; you could also throw a custom exception instead
        }
    }
    @Override
    public void updateTemplate(TemplateModel updatedModel, String projectName, String oldTemplateName)
            throws ParserConfigurationException, IOException, SAXException, TransformerException {

        String basePath = "generated-projects/" + projectName + "/ui.content/src/main/content/jcr_root/conf/" +
                projectName + "/settings/wcm/templates/";
        String targetpath=basePath+updatedModel.getTitle();
        File oldFolder = new File(basePath + oldTemplateName);
        File newFolder = new File(basePath + updatedModel.getTitle());

        if (!oldFolder.exists()) {
            throw new FileNotFoundException("Old template folder not found: " + oldFolder.getAbsolutePath());
        }

        // Safely rename folder
        try {
            Files.move(oldFolder.toPath(), newFolder.toPath(), StandardCopyOption.REPLACE_EXISTING);

        } catch (IOException e) {

            newFolder.mkdirs();
            for (File file : oldFolder.listFiles()) {
                Files.move(file.toPath(), new File(newFolder, file.getName()).toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
            oldFolder.delete();
        }

        if(updatedModel.getTemplateType().equals("page")) {
            writeFile(targetpath + "/structure/.content.xml", TemplateUtil.getStructureXmlPage(updatedModel.getTitle(),
                    projectName)); // Pass projectname here
            writeFile(targetpath + "/policies/.content.xml", TemplateUtil.getPoliciesPage(projectName));

        }
        else {

            writeFile(targetpath + "/structure/.content.xml", TemplateUtil.generateStructureContentXmlXf(projectName,
                    updatedModel.getTitle())); // Pass projectname here
            writeFile(targetpath+"/policies/.content.xml",TemplateUtil.generatePoliciesXmlXf(projectName));

        }


        // ✅ 1. Update the template's own .content.xml
        File templateContentFile = new File(newFolder, ".content.xml");
        if (templateContentFile.exists()) {
            DocumentBuilder builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
            Document doc = builder.parse(templateContentFile);
            Element root = doc.getDocumentElement();
            Element content = (Element) root.getElementsByTagName("jcr:content").item(0);

            root.setAttribute("jcr:title", updatedModel.getTitle()); // updates root title
            if (updatedModel.getTitle() != null) content.setAttribute("jcr:title", updatedModel.getTitle());
            if (updatedModel.getDescription() != null) content.setAttribute("jcr:description", updatedModel.getDescription());
            if (updatedModel.getStatus() != null) content.setAttribute("status", updatedModel.getStatus());

            if (updatedModel.getTemplateType() != null) {
                content.setAttribute("cq:templateType", "/conf/" + projectName + "/settings/wcm/template-types/" + updatedModel.getTemplateType());
            }





            // Save updated template .content.xml
            Transformer transformer = TransformerFactory.newInstance().newTransformer();
            transformer.transform(new DOMSource(doc), new StreamResult(templateContentFile));



        }


        //update intial/.content.xml
        String intial=newFolder+"/initial";

        File intialContentFile=new File(intial,".content.xml");

        if(intialContentFile.exists()){
            DocumentBuilder builder1 = DocumentBuilderFactory.newInstance().newDocumentBuilder();
            Document doc1 = builder1.parse(intialContentFile);
            Element root1 = doc1.getDocumentElement();
            Element content1 = (Element) root1.getElementsByTagName("jcr:content").item(0);
            String cqTemplate="/conf/"+projectName+"/settings/wcm/templates/"+updatedModel.getTitle();
            content1.setAttribute("cq:template",cqTemplate);
            content1.setAttribute("sling:resourceType",projectName+"/components/xfpage");

            Transformer transformer = TransformerFactory.newInstance().newTransformer();
            transformer.transform(new DOMSource(doc1), new StreamResult(intialContentFile));
        }




        // ✅ 2. Update the parent folder's .content.xml (register template if not present)
        File parentContentFile = new File(basePath + ".content.xml");
        if (parentContentFile.exists()) {
            DocumentBuilder builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
            Document doc = builder.parse(parentContentFile);
            Element root = doc.getDocumentElement();

            // Remove old template node if present
            Node oldNode = null;
            NodeList children = root.getChildNodes();
            for (int i = 0; i < children.getLength(); i++) {
                Node n = children.item(i);
                if (n.getNodeType() == Node.ELEMENT_NODE && n.getNodeName().equals(oldTemplateName)) {
                    oldNode = n;
                    break;
                }
            }
            if (oldNode != null) {
                root.removeChild(oldNode);

            }

            // Add new template node if missing
            if (doc.getElementsByTagName(updatedModel.getTitle()).getLength() == 0) {
                Element newElement = doc.createElement(updatedModel.getTitle());
                root.appendChild(newElement);

            }

            // Save updated XML
            Transformer transformer = TransformerFactory.newInstance().newTransformer();

            transformer.transform(new DOMSource(doc), new StreamResult(parentContentFile));
        }

    }



}

