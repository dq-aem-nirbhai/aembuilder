package com.aem.builder.service;


import com.aem.builder.model.AemProjectModel;
import com.aem.builder.model.ProjectDetails;

import java.util.List;

public interface AemProjectService {
    void generateAemProject(AemProjectModel aemProjectModel);
    List<ProjectDetails> getAllProjects();
    byte[] getProjectZip(String projectName) throws java.io.IOException;
    void importProject(org.springframework.web.multipart.MultipartFile file) throws java.io.IOException;

}
