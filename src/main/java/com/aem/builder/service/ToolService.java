package com.aem.builder.service;
 
import java.util.List;
 
public interface ToolService {
    void addToolsToExistingProject(String projectName, List<String> selectedTools);
    public List<String> getExistingTools(String projectName);
    public List<String>fetchTools( String projectName);

}