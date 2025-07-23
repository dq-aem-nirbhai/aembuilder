package com.aem.builder.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AemProjectModel {

    private String projectName;   // appTitle
    private String version;       // aemVersion
    private String packageName;//packageName
    private List<String> selectedComponents;
    private List<String> selectedTemplates;

}


