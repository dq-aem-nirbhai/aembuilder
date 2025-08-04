package com.aem.builder.model.policy;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * A group of styles in a policy. Groups may optionally allow style
 * combination similar to the behaviour in AEM's policy editor.
 */
@Data
public class StyleGroup {
    private String name;
    private boolean allowCombination;
    private List<StyleItem> styles = new ArrayList<>();
}
