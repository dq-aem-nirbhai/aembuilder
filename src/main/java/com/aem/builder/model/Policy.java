package com.aem.builder.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Policy {
    private String id;
    private String name;
    private String title;
    private String defaultClass;
    private List<StyleGroup> groups = new ArrayList<>();
}
