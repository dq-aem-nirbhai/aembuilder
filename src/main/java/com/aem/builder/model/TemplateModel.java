package com.aem.builder.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class TemplateModel {

    private String name;
    private String title;
    private String description;
    private String status;
    private String templateType; // <--- CHANGE THIS TO camelCase

}