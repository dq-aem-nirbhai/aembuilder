package com.aem.builder.service;

import com.aem.builder.model.DTO.ComponentField;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

public interface UpdateHTL {
    public String updateHTLFromDialog(String htlContent, List<ComponentField> fields);
    public void updateHTLFile(Path htlFile, List<ComponentField> fields) throws IOException;
}
