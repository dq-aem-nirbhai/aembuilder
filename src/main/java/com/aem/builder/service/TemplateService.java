package com.aem.builder.service;

import com.aem.builder.model.TemplateModel;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import java.io.IOException;
import java.util.List;



public interface TemplateService {
    public List<String> getTemplateFileNames() throws IOException;

    public void copySelectedTemplatesToGeneratedProject(String projectName, List<String> selectedTemplates) throws IOException;

    public List<String> getDistinctTemplates(String projectname, List<String> resourceTemplates, List<String> projectTemplates);

    public List<String> getCommonTemplates(List<String> resourcetemplates, List<String> projectTemplates);

    public List<String> getTemplateNamesFromDestination(String projectName);

    List<String> fetchTemplatesFromGeneratedProjects(String projectName);

    //creating template
    TemplateModel createTemplate(TemplateModel model, String projectname) throws IOException;



    public List<String> getTemplateTypesFromDestination(String projectName);

    public TemplateModel loadTemplateByName(String templateName, String projectName);


    public void updateTemplate(TemplateModel updatedModel, String projectName, String oldTemplateName) throws ParserConfigurationException, IOException, SAXException, TransformerException;
}