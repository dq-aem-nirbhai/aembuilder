package com.aem.builder.model.DTO;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ComponentRequest {
    private String projectName;
    private String componentName;
    private String componentGroup;
    private List<ComponentField> fields;

}
