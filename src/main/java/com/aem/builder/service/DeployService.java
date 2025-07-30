package com.aem.builder.service;

public interface DeployService {
    String deployProject(String projectName, String module) throws Exception;
    String extractErrorMessage(String fullOutput);
}
