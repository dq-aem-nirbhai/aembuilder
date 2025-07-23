package com.aem.builder.service;

import java.io.IOException;
import java.util.List;

import java.util.List;

public interface TemplateService {
    public List<String> getTemplateFileNames() throws IOException;
    public void copySelectedTemplatesToGeneratedProject(String projectName, List<String> selectedTemplates) throws IOException;
    public List<String>getDistinctTemplates(String projectname,List<String>resourceTemplates,List<String>projectTemplates);
    public List<String>getCommonTemplates(List<String>resourcetemplates,List<String>projectTemplates);
    public List<String> getTemplateNamesFromDestination(String projectName);
    List<String> fetchTemplatesFromGeneratedProjects(String projectName);

}
