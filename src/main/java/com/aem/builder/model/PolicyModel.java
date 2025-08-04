package com.aem.builder.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PolicyModel {
    private String name;
    private String title;
    private String description;
    private String defaultCssClass;
    private List<StyleGroupModel> styleGroups = new ArrayList<>();
}
