package com.aem.builder.service;
 
import java.util.List;
 
public interface ToolService {
    void addToolsToExistingProject(String projectName, List<String> selectedTools);

}