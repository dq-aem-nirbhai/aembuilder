package com.aem.builder.model;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * Model representing policy configuration for a component within a template.
 */
@Data
public class PolicyModel {
    private String policyName;
    private String title;
    private String defaultClass;
    private List<StyleGroup> groups = new ArrayList<>();

    @Data
    public static class StyleGroup {
        private String name;
        private List<Style> styles = new ArrayList<>();
    }

    @Data
    public static class Style {
        private String name;
        private String className;
    }
}

