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
public class TemplateModel {
    private List<String> resourceTemplates;
    private List<String>projectTemplates;
}
