package com.aem.builder.service;


import com.aem.builder.model.AemProjectModel;
import com.aem.builder.model.ProjectDetails;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

public interface AemProjectService {
    void generateAemProject(AemProjectModel aemProjectModel) throws IOException;
    List<ProjectDetails> getAllProjects();
    byte[] getProjectZip(String projectName) throws IOException;
    void importProject(org.springframework.web.multipart.MultipartFile file) throws IOException;
    void deleteProject(String projectName) throws IOException;
    boolean projectExists(String projectName);
    String extractArtifactId(MultipartFile file) throws IOException;

}
