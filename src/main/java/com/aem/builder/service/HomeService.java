package com.aem.builder.service;

import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/**
 * Service for operations related to the dashboard/home page.
 */
public interface HomeService {

    /**
     * Import a zipped AEM project into the local generated projects directory.
     *
     * @param file the uploaded zip file
     * @throws IOException              if file processing fails
     * @throws IllegalArgumentException if the uploaded file is not a valid AEM project
     */
    void importProject(MultipartFile file) throws IOException;
}
