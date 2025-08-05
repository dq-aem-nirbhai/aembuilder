package com.aem.builder.model.policy;

import lombok.Data;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents an AEM component policy with style groups.
 */
@Data
public class PolicyModel {
    private String id; // folder name of policy
    private String title;
    private String description;
    private String defaultCssClass;
    private List<StyleGroupModel> styleGroups = new ArrayList<>();
}
