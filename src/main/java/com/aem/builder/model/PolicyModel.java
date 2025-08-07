package com.aem.builder.model;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents an AEM component policy with style groups.
 */
@Data
@RequiredArgsConstructor
public class PolicyModel {
    private String id;
    private String title;
    private String description;
    private String defaultCssClass;
    private List<StyleGroupModel> styleGroups = new ArrayList<>();
}
