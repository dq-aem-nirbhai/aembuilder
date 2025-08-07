package com.aem.builder.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.Setter;

import java.util.Map;
@Setter
@Getter

@NoArgsConstructor
public class PolicyRequest {

    private String name;
    private String componentPath;
    private String styleDefaultClasses;
    private String styleDefaultElement;
    private Map<String, Map<String, String>> styles;


}