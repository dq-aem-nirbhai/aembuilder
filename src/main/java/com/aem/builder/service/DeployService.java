package com.aem.builder.service;

public interface DeployService {
    String deployProject(String projectName) throws Exception;
    String extractErrorMessage(String fullOutput);
}
