package com.aem.builder.service;

import com.aem.builder.model.TemplateModel;

import java.io.IOException;
import java.util.List;



public interface TemplateService {
    public List<String> getTemplateFileNames() throws IOException;
    public void copySelectedTemplatesToGeneratedProject(String projectName, List<String> selectedTemplates) throws IOException;
    public List<String>getDistinctTemplates(String projectname,List<String>resourceTemplates,List<String>projectTemplates);
    public List<String>getCommonTemplates(List<String>resourcetemplates,List<String>projectTemplates);
    public List<String> getTemplateNamesFromDestination(String projectName);
    List<String> fetchTemplatesFromGeneratedProjects(String projectName);


    //creating template
    void createTemplate(TemplateModel model, String projectname) throws IOException;
    //template type xf
    void createTemplateXf(TemplateModel model,String projectname) throws IOException;


}
