package com.aem.builder.service;


import com.aem.builder.model.AemProjectModel;
import com.aem.builder.model.ProjectDetails;

import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface AemProjectService {
    void generateAemProject(AemProjectModel aemProjectModel);
    List<ProjectDetails> getAllProjects();
    void importAemProject(MultipartFile file) throws Exception;

}
