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
    /**
     * Optional component that this component extends. This value will be used as
     * {@code sling:resourceSuperType} when generating the component's
     * {@code .content.xml}.
     */
    private String superType;
    private List<ComponentField> fields;

}
