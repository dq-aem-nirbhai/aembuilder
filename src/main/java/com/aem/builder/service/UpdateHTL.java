package com.aem.builder.service;

import com.aem.builder.model.DTO.ComponentField;
import com.aem.builder.model.DTO.ComponentRequest;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

public interface UpdateHTL {
    public void updateHTLFromRequest(ComponentRequest request, String filePath,String projectName,ComponentRequest oldRequest) throws IOException;
}
