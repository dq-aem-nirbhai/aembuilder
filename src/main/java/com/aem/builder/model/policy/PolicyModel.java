package com.aem.builder.model.policy;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.w3c.dom.Node;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Represents an AEM component policy with style groups.
 */
@Data
@RequiredArgsConstructor
public class PolicyModel {
    private String id; // folder name of policy
    private String title;
    private String description;
    private String defaultCssClass;
    private List<StyleGroupModel> styleGroups = new ArrayList<>();
}
