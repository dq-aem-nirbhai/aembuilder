package com.aem.builder.service;

import com.aem.builder.model.AemProjectModel;
import com.aem.builder.model.ProjectDetails;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

/**
 * Service interface for managing AEM projects.
 * Provides operations for creating, retrieving, importing, exporting, deleting, and cloning projects.
 */
public interface AemProjectService {

    /**
     * Generates a new AEM project based on the provided project model.
     *
     * @param projectModel the project configuration
     * @throws IOException if project generation fails
     */
    void generateProject(AemProjectModel projectModel, boolean lombok) throws IOException;

    /**
     * Retrieves the list of all existing projects.
     *
     * @return list of project details
     */
    List<ProjectDetails> getAllProjects();

    /**
     * Downloads the AEM project as a ZIP file.
     *
     * @param projectName the project name
     * @return byte array representing the ZIP content
     * @throws IOException if project ZIP generation fails
     */
    byte[] downloadProjectZip(String projectName) throws IOException;

    /**
     * Imports an existing AEM project from a ZIP file.
     *
     * @param file the uploaded ZIP file
     * @throws IOException if import fails
     */
    void importProject(MultipartFile file) throws IOException;

    /**
     * Deletes an existing AEM project.
     *
     * @param projectName the project name
     * @throws IOException if deletion fails
     */
    void deleteProject(String projectName) throws IOException;

    /**
     * Checks if a project with the given name already exists.
     *
     * @param projectName the project name
     * @return true if project exists, false otherwise
     */
    boolean projectExists(String projectName);

    /**
     * Extracts the Maven artifactId from the uploaded ZIP file.
     *
     * @param file the uploaded ZIP file
     * @return the artifactId
     * @throws IOException if extraction fails
     */
    String extractArtifactId(MultipartFile file) throws IOException;

    /**
     * Clones a project from a given Git repository URL.
     *
     * @param repoUrl the Git repository URL
     * @throws IOException if cloning fails
     */
    void cloneProject(String repoUrl) throws IOException;
}
