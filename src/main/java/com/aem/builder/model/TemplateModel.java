


 package com.aem.builder.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

// import java.util.List; // Not used, can be removed if not needed

@Data // This generates public getters and setters like getTemplateType() and setTemplateType()
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