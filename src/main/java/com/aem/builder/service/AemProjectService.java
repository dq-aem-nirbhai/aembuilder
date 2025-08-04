package com.aem.builder.service;


import com.aem.builder.model.AemProjectModel;
import com.aem.builder.model.ProjectDetails;

import java.util.List;

public interface AemProjectService {
    void generateAemProject(AemProjectModel aemProjectModel);
    List<ProjectDetails> getAllProjects();

}
