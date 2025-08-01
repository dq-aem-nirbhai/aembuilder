package com.aem.builder.model.policy;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PolicyModel {
    private String policyName;
    private String styleDefaultClasses;
    private List<StyleGroup> styleGroups = new ArrayList<>();
}
