package com.aem.builder.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ComponentPolicy {
    private String title;
    private String description;
    private String defaultClass;
    private List<StyleGroup> groups = new ArrayList<>();
}
