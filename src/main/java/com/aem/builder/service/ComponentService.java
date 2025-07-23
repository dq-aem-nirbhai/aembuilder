package com.aem.builder.service;

import java.util.List;

public interface ComponentService {
    List<String> fetchComponentsFromGeneratedProjects(String projectName);
}
