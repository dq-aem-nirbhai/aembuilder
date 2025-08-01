package com.aem.builder.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class PolicyModel {
    private String policyName;
    private String componentName;
    private String templateName;
    private String styleClassName;
    private String styleName;
    private String group;
}
