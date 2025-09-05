package com.aem.builder.service;

import com.aem.builder.model.DTO.ComponentField;
import com.aem.builder.model.DTO.ComponentRequest;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

public interface UpdateComponent {
    public void updateDialog(File xmlFile, List<ComponentField> fields) throws Exception;

    public void updateSlingModel(ComponentRequest request) throws IOException;

    public void updateHTLTextOnly(ComponentRequest request, ComponentRequest oldRequest)throws IOException;



}
