package com.aem.builder.model;

import lombok.Data;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents a group of styles in a policy.
 */
@Data
public class StyleGroupModel {
    private String name;
    private boolean allowCombination;
    private List<StyleModel> styles = new ArrayList<>();
}
