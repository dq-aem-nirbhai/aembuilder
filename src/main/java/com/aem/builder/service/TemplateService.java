package com.aem.builder.service;

import com.aem.builder.model.TemplateModel;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerException;
import java.io.IOException;
import java.util.List;

/**
 * Service interface for managing templates in an AEM project.
 * Provides methods for retrieving, creating, updating, and copying templates.
 */
public interface TemplateService {

    /**
     * Retrieves the list of template file names from the resource directory.
     */
    List<String> getTemplateFileNames() throws IOException;

    /**
     * Copies selected templates from the resource directory to the specified project's template directory.
     */
    void copySelectedTemplatesToGeneratedProject(String projectName, List<String> selectedTemplates) throws IOException;

    /**
     * Retrieves templates that are present in the resource templates but not yet added to the project.
     */
    List<String> getDistinctTemplates(String projectname, List<String> resourceTemplates, List<String> projectTemplates);

    /**
     * Retrieves templates that are common between resource templates and project templates.
     */
    List<String> getCommonTemplates(List<String> resourceTemplates, List<String> projectTemplates);

    /**
     * Retrieves the list of template names from the project's template directory,
     * excluding XML files.
     */
    List<String> getTemplateNamesFromDestination(String projectName);

    /**
     * Fetches all templates available in the generated project's template directory.
     */
    List<String> fetchTemplatesFromGeneratedProjects(String projectName);

    /**
     * Creates a new template with directory structure and necessary XML files
     * based on the provided model.
     */
    TemplateModel createTemplate(TemplateModel model, String projectname) throws IOException;

    /**
     * Retrieves the list of template types from the project's template types directory,
     * excluding XML files.
     */
    List<String> getTemplateTypesFromDestination(String projectName);

    /**
     * Loads a template's details from its .content.xml file based on the template name and project name.
     */
    TemplateModel loadTemplateByName(String templateName, String projectName);

    /**
     * Updates an existing template's folder, files, and XML metadata.
     */
    void updateTemplate(TemplateModel updatedModel, String projectName, String oldTemplateName)
            throws ParserConfigurationException, IOException, SAXException, TransformerException;
}
