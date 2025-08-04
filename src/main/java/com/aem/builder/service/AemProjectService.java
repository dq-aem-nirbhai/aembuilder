package com.aem.builder.service;


import com.aem.builder.model.AemProjectModel;

import java.util.List;

public interface AemProjectService {

    void generateAemProject(AemProjectModel aemProjectModel);

    /**
     * Returns the names of AEM projects that have already been generated
     * in the workspace. This is used by the UI to warn users if they are
     * about to create a project that already exists.
     *
     * @return list of project directory names, never {@code null}
     */
    List<String> getExistingProjects();
}
