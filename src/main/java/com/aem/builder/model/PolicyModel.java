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
public class PolicyModel {
    private String title;
    private String description;
    private String defaultCssClasses;
    private List<String> styleNames;
    private List<String> styleClasses;
}
