package com.aem.builder.service.impl;

import com.aem.builder.constants.AemProjectConstants;
import com.aem.builder.model.TemplateModel;
import com.aem.builder.service.TemplateService;

import com.aem.builder.util.TemplateUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
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
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static com.aem.builder.constants.PolicyConstants.*;
import static com.aem.builder.util.TemplateUtil.writeFile;


@Service
@RequiredArgsConstructor
@Slf4j
public class TemplateServiceImpl implements TemplateService {
    private final List<TemplateModel>templateModels;
    private final ResourceLoader resourceLoader;

    /**
     * Retrieves the list of template file names from the 'aem-templates' directory in the classpath.
     * It uses the ClassLoader to access the folder and list all files inside it.
     * If the folder or files are not found, it returns an empty list.
     *
     */
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
                        log.info("[getTemplateFileNames] Found template file - {}", file.getName());
                    }
                }
            }
            else{
                log.info("[getTemplateFileNames] 'aem-templates' directory not found in classpath.");
            }
        }
        log.info("getTemplateFileNames: Completed fetching template file names. Total templates found: {}", templateNames.size());
        return templateNames;
    }

    /**
     * Copies the selected template folders from the classpath resource directory
     * into the specified project's templates directory.
     * It ensures the destination folder exists, and for each selected template,
     * it copies the template's files from the resource location to the project's template directory.
     *
     */
    @Override
    public void copySelectedTemplatesToGeneratedProject(String projectName, List<String> selectedTemplates)
            throws IOException {
        log.info("[copySelectedTemplatesToGeneratedProject] Starting to copy selected templates to project '{}'",
                projectName);

        // 1. Define destination
        String destinationPath = TemplateUtil.getTemplatePath(projectName);
        File destinationFolder = new File(destinationPath);
        if (!destinationFolder.exists()) {
            destinationFolder.mkdirs(); // create nested folders
            log.info("[copySelectedTemplatesToGeneratedProject] Created destination folder '{}'",
                    destinationFolder.getAbsolutePath());

        }
        else{
            log.info("[copySelectedTemplatesToGeneratedProject] Destination folder already exists '{}'",
                    destinationFolder.getAbsolutePath());

        }
        for (String templateName : selectedTemplates) {
            // Load template from classpath
            File resource = new File(RESOURCE_TEMPLATES_DIR + templateName);
            File targetFile = new File(destinationFolder, templateName);

            log.info("[copySelectedTemplatesToGeneratedProject] Copying template '{}' from '{}' to '{}'",
                    templateName, resource.getAbsolutePath(), targetFile.getAbsolutePath());
            FileUtils.copyDirectory(resource, targetFile);
        }
    }

    /**
     * Returns a list of templates that are present in the resource templates
     * but not in the project templates.
     * This helps in identifying templates that are available for use but not yet added to the project.
     */
    @Override
    public List<String> getDistinctTemplates(String projectname,
                                             List<String> resourceTemplates,List<String>projectTemplates) {
        log.info("[getDistinctTemplates] Computing distinct templates for project '{}'", projectname);
        List<String>distinct=new ArrayList<>();
        distinct= resourceTemplates.stream()
                .filter(t -> !projectTemplates.contains(t))
                .collect(Collectors.toList());
        log.info("[getDistinctTemplates] Found {} distinct templates for project '{}'", distinct.size(), projectname);
        log.info("[getDistinctTemplates] Distinct templates: {}", distinct);
        return distinct;
    }

    /**
     * Finds and returns the common templates that are present in both
     * the resourceTemplates and projectTemplates lists.
     */
    @Override
    public List<String>getCommonTemplates(List<String> resourceTemplates,List<String>projectTemplates){
        log.info("[getCommonTemplates] Computing common templates");
        List<String> commonTemplates = resourceTemplates.stream()
                .filter(projectTemplates::contains)
                .collect(Collectors.toList());

        log.info("[getCommonTemplates] Found {} common templates", commonTemplates.size());
        log.info("[getCommonTemplates] Common templates: {}", commonTemplates);
        return commonTemplates;

    }

    /**
     * Retrieves the list of template names from the project's template directory,
     * excluding any XML files.
     *
     */
    @Override
    public List<String> getTemplateNamesFromDestination(String projectName) {
        log.info("[getTemplateNamesFromDestination] Starting to fetch template names for project '{}'", projectName);
        List<String> templateNames = new ArrayList<>();
        String destinationPath = TemplateUtil.getTemplatePath(projectName);
        File folder = new File(destinationPath);
        if (folder.exists() && folder.isDirectory()) {
            log.info("[getTemplateNamesFromDestination] Found directory '{}'", destinationPath);
            File[] files = folder.listFiles();
            if (files != null) {
                for (File file : files) {
                    String filename=file.getName();
                    if(!filename.endsWith(".xml")) {
                        templateNames.add(filename);
                        log.info("[getTemplateNamesFromDestination] Added template '{}'", filename);
                    }
                }
            }

        }
        else{
            log.warn("[getTemplateNamesFromDestination] Directory '{}' does not exist or is not a directory",
                    destinationPath);
        }
        log.info("[getTemplateNamesFromDestination] Completed. Total templates found: {}", templateNames.size());

        return templateNames;
    }

    /**
     * Retrieves the list of template type names from the project's template-types directory,
     * excluding any XML files.
     */
    @Override
    public List<String> getTemplateTypesFromDestination(String projectName) {
        log.info("[getTemplateTypesFromDestination] Starting to fetch template types for project '{}'", projectName);
        List<String> templateNames = new ArrayList<>();
        String destinationPath = TemplateUtil.getTemplateTypesPath(projectName);
        File folder = new File(destinationPath);
        if (folder.exists() && folder.isDirectory()) {
            log.info("[getTemplateTypesFromDestination] Found directory '{}'", destinationPath);
            File[] files = folder.listFiles();
            if (files != null) {
                for (File file : files) {
                    String filename=file.getName();
                    if(!filename.endsWith(".xml")) {
                        templateNames.add(filename);
                        log.info("[getTemplateTypesFromDestination] Added template type '{}'", filename);
                    }
                }
            }
        }
        return templateNames;
    }

    /**
     * Fetches the list of templates from the generated project's template directory.
     * It returns the names of all subdirectories which represent templates.
     */
    @Override
    public List<String> fetchTemplatesFromGeneratedProjects(String projectName) {
        log.info("[fetchTemplatesFromGeneratedProjects] Fetching templates for project '{}'", projectName);
        File templatesDir = new File(TemplateUtil.getTemplatePath(projectName));
        if (templatesDir.exists()) {
            log.info("[fetchTemplatesFromGeneratedProjects] Found templates directory '{}'", templatesDir.getAbsolutePath());
            return Arrays.stream(templatesDir.listFiles(File::isDirectory))
                    .map(File::getName)
                    .collect(Collectors.toList());
        }
        log.warn("[fetchTemplatesFromGeneratedProjects] Templates directory '{}' does not exist", templatesDir.getAbsolutePath());
        return List.of();
    }

    /**
     * Creates a new template directory structure along with necessary XML files
     * based on the provided TemplateModel and project name.
     * The method handles both "page" and "xf" template types.
     */
    @Override
    public TemplateModel createTemplate(TemplateModel model, String projectName) throws IOException {
        log.info("[createTemplate:] Creating template '{}' for project '{}'", model.getName(), projectName);
        String url = TemplateUtil.getTemplateParent(projectName,model.getName());

        // Create parent directory
        new File(url).mkdirs();
        log.info("[createTemplate] Created directory '{}'", url);

        // Create subfolders
        new File(TemplateUtil.getIntialFilePath(projectName,model.getName())).mkdirs();
        new File(TemplateUtil.getStructureFilePath(projectName,model.getName())).mkdirs();
        new File(TemplateUtil.getPoliciesFilePath(projectName,model.getName())).mkdirs();

        // Write XML files
        writeFile(TemplateUtil.getRootContentFilePath(projectName,model.getName()),
                TemplateUtil.getTemplateRootXmlPage(model.getName(),projectName,
                        model.getTemplateType(),model.getStatus(),model.getDescription()));

        if(model.getTemplateType().equals("page")) {
            writeFile(TemplateUtil.getIntialContentFile(projectName,model.getName()),
                    TemplateUtil.getInitialXmlPage(projectName,
                    model.getName()));
            writeFile(TemplateUtil.getStructureContentFile(projectName,model.getName()),
                    TemplateUtil.getStructureXmlPage(model.getName(),
                    projectName));
            writeFile(TemplateUtil.getPoliciesContentFile(projectName,model.getName()),
                    TemplateUtil.getPoliciesPage(projectName));
            log.info("[createTemplate] Page type XML files created for template '{}'", model.getName());
        }
        else {

            writeFile(TemplateUtil.getIntialContentFile(projectName,model.getName()),
                    TemplateUtil.getIntialContentXf(projectName,
                    model.getName()));
            writeFile(TemplateUtil.getStructureContentFile(projectName,model.getName()),
                    TemplateUtil.generateStructureContentXmlXf(projectName,
                    model.getName()));
            writeFile(TemplateUtil.getPoliciesContentFile(projectName,model.getName()),
                    TemplateUtil.generatePoliciesXmlXf(projectName));
            log.info("[createTemplate:] XF type XML files created for template '{}'", model.getName());
        }

        File xmlFile = new File(TemplateUtil.getPathOfTemplateContent(projectName));
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
        String newTemplateName = model.getName(); // from user
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
                log.info("[createTemplate] Registered template '{}' in parent XML", newTemplateName);
            } catch (TransformerException e) {
                log.error("[createTemplate] Failed to update parent XML for template '{}'", model.getName(), e);
                throw new RuntimeException(e);
            }
        }

        return model;
    }

    /**
     * Loads a template's details from its .content.xml file based on the project name and template name.
     * Parses the XML and maps attributes to the TemplateModel.
     */
    @Override
    public TemplateModel loadTemplateByName(String projectName, String templateName) {
        log.info("[loadTemplateByName] Loading template '{}' from project '{}'", templateName, projectName);
        try {
            File contentXmlFile = new File(TemplateUtil.getRootContentFilePath(projectName,templateName));
            if (!contentXmlFile.exists()) {
                log.warn("[loadTemplateByName] .content.xml not found for template '{}'", templateName);
                throw new FileNotFoundException(".content.xml not found for template: " + templateName);
            }
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(contentXmlFile);
            Element root = doc.getDocumentElement();

            TemplateModel model = new TemplateModel();
            model.setName(templateName);
            model.setTitle(root.getAttribute(ATTR_JCR_TITLE ));

            Element content = (Element) root.getElementsByTagName( JCR_CONTENT_TAG).item(0);
            model.setTitle(content.getAttribute(ATTR_JCR_TITLE ));
            model.setStatus(content.getAttribute(STATUS));
            model.setDescription(content.getAttribute(ATT_DESCRIPTION));
            String templateType=content.getAttribute(ATTR_TEMPLATE_TYPE);
            if (templateType != null && templateType.contains("/")) {
                model.setTemplateType(templateType.substring(templateType.lastIndexOf("/") + 1));
            }
            log.info("[loadTemplateByName] Loaded template model: {}", model);
            return model;

        } catch (Exception e) {
            log.error("[loadTemplateByName] Failed to load template '{}'", templateName, e);
            e.printStackTrace();
            return null; // <- fallback; you could also throw a custom exception instead
        }
    }

    /**
     * Updates an existing template by renaming the folder and updating its XML files.
     * It ensures the template's details are consistent and registers the template in the parent directory.
     * Handles both "page" and "xf" template types.
     */
    @Override
    public void updateTemplate(TemplateModel updatedModel, String projectName, String oldTemplateName)
            throws ParserConfigurationException, IOException, SAXException, TransformerException {
        String basePath = GENERATED_PROJECTS_PATH+ projectName + UI_CONTENT_PATH  +
                projectName + "/settings/wcm/templates/";
        String targetpath=basePath+updatedModel.getName();
        File oldFolder = new File(basePath + oldTemplateName);
        File newFolder = new File(basePath + updatedModel.getName());

        if (!oldFolder.exists()) {
            throw new FileNotFoundException("Old template folder not found: " + oldFolder.getAbsolutePath());
        }

        // Safely rename folder
        try {
            Files.move(oldFolder.toPath(), newFolder.toPath(), StandardCopyOption.REPLACE_EXISTING);
            log.info("[updatedTemplate] Folder renamed successfully.");
        } catch (IOException e) {
            log.info("[updatedTemplate] Rename failed, attempting manual copy...");
            newFolder.mkdirs();
            for (File file : oldFolder.listFiles()) {
                Files.move(file.toPath(), new File(newFolder, file.getName()).toPath(),
                        StandardCopyOption.REPLACE_EXISTING);
            }
            oldFolder.delete();
        }

        if(updatedModel.getTemplateType().equals("page")) {
            writeFile(targetpath + STRUCTURE+ CONTENT_FILE,
                    TemplateUtil.getStructureXmlPage(updatedModel.getName(),
                    projectName)); // Pass projectname here
            writeFile(targetpath + POLICIES+CONTENT_FILE,
                    TemplateUtil.getPoliciesPage(projectName));

        }
        else {

            writeFile(targetpath +STRUCTURE+ CONTENT_FILE,
                    TemplateUtil.generateStructureContentXmlXf(projectName,
                    updatedModel.getName())); // Pass projectname here
            writeFile(targetpath+ POLICIES+CONTENT_FILE,
                    TemplateUtil.generatePoliciesXmlXf(projectName));

        }

        //  1. Update the template's own .content.xml
        File templateContentFile = new File(newFolder, ".content.xml");
        if (templateContentFile.exists()) {
            DocumentBuilder builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
            Document doc = builder.parse(templateContentFile);
            Element root = doc.getDocumentElement();
            Element content = (Element) root.getElementsByTagName(JCR_CONTENT_TAG).item(0);

            root.setAttribute( ATTR_JCR_TITLE , updatedModel.getName()); // updates root title
            if (updatedModel.getTitle() != null) content.setAttribute(ATTR_JCR_TITLE,
                    updatedModel.getTitle());
            if (updatedModel.getStatus() != null) content.setAttribute(STATUS,
                    updatedModel.getStatus());

            if (updatedModel.getTemplateType() != null) {
                content.setAttribute(ATTR_TEMPLATE_TYPE, CONF_PATH +
                        projectName + WCM_TEMPLATE_TYPES_PATH  + updatedModel.getTemplateType());
            }

            // Save updated template .content.xml
            Transformer transformer = TransformerFactory.newInstance().newTransformer();
            transformer.transform(new DOMSource(doc), new StreamResult(templateContentFile));


            log.info("[updatedTemplate] Updated template .content.xml");
        }
        //update intial/.content.xml
        String intial=newFolder+INITIAL;


        File intialContentFile=new File(intial,".content.xml");
        log.info("[updatedTemplate] intial location");
        if(intialContentFile.exists()){
            DocumentBuilder builder1 = DocumentBuilderFactory.newInstance().newDocumentBuilder();
            Document doc1 = builder1.parse(intialContentFile);
            Element root1 = doc1.getDocumentElement();
            Element content1 = (Element) root1.getElementsByTagName(JCR_CONTENT_TAG).item(0);
            String cqTemplate=CONF_PATH +projectName+TEMPLATES_SUBPATH +updatedModel.getName();
            content1.setAttribute("cq:template",cqTemplate);
            content1.setAttribute(ATTR_SLING_RESOURCE_TYPE,projectName+"/components/xfpage");
            //"project_1/components/xfpage"
            log.info("[updatedTemplate]  updated cq template field");
            Transformer transformer = TransformerFactory.newInstance().newTransformer();
            transformer.transform(new DOMSource(doc1), new StreamResult(intialContentFile));
        }

        //  2. Update the parent folder's .content.xml (register template if not present)
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
                log.info("[updatedTemplate] Removed old template node: " + oldTemplateName);
            }

            // Add new template node if missing
            if (doc.getElementsByTagName(updatedModel.getName()).getLength() == 0) {
                Element newElement = doc.createElement(updatedModel.getName());
                root.appendChild(newElement);

                log.info("[updatedTemplate] Added new template node: " + updatedModel.getName());
            }

            // Save updated XML
            Transformer transformer = TransformerFactory.newInstance().newTransformer();

            transformer.transform(new DOMSource(doc), new StreamResult(parentContentFile));
           log.info("[updatedTemplate] updated the template");
        }

    }
}
