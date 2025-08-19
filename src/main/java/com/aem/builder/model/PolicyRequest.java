package com.aem.builder.model;

import lombok.*;

import java.util.Map;
@Setter
@Getter
@ToString
@NoArgsConstructor
public class PolicyRequest {

    private String name;
    private String componentPath;
    private String styleDefaultClasses;
    private String styleDefaultElement;
    private Map<String, Map<String, String>> styles;

}