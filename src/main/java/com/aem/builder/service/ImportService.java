package com.aem.builder.service;

import com.aem.builder.exception.ProjectAlreadyExistsException;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

public interface ImportService {
    String importProject(MultipartFile file) throws IOException, ProjectAlreadyExistsException;
}
