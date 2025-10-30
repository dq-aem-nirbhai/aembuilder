package com.aem.builder.service;

import java.io.IOException;
import java.util.List;
import java.util.Map;

public interface ArtifactService {
    Map<String, List<ArtifactFile>> listArtifacts() throws IOException;
    void generateArtifacts(String projectName, List<String> selectedFiles) throws IOException;
    void generateDynamicArtifact(String projectName, String type, Map<String, String> params) throws IOException;

    class ArtifactFile {
        public String name;
        public String relativePath;
        public String packageInfo;
        public String safeId;

        public ArtifactFile(String name, String relativePath, String packageInfo) {
            this.name = name;
            this.relativePath = relativePath;
            this.packageInfo = packageInfo;
            this.safeId = relativePath.replaceAll("[^a-zA-Z0-9]", "_");
        }
    }
}
