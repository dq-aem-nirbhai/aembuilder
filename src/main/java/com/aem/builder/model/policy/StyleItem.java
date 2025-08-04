package com.aem.builder.model.policy;

import lombok.Data;

/**
 * Represents a single style with a label and the CSS class that should be
 * applied when selected.
 */
@Data
public class StyleItem {
    private String name;
    private String cssClass;
}
