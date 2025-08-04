package com.aem.builder.model.policy;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a simplified AEM component policy. This model is intentionally
 * lightweight and only captures the fields required by the UI. The structure
 * roughly mirrors the policy editor in AEM allowing a default CSS class and
 * multiple style groups with styles.
 */
@Data
public class PolicyModel {
    private String title;
    private String description;
    private String defaultCssClass;
    private List<StyleGroup> styleGroups = new ArrayList<>();
}
