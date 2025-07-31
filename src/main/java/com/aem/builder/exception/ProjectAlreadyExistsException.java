package com.aem.builder.exception;

public class ProjectAlreadyExistsException extends Exception {
    private final String projectName;

    public ProjectAlreadyExistsException(String projectName) {
        super("Project already exists: " + projectName);
        this.projectName = projectName;
    }

    public String getProjectName() {
        return projectName;
    }
}
