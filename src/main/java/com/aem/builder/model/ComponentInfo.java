package com.aem.builder.model;

import lombok.AllArgsConstructor;
import lombok.Data;

@AllArgsConstructor
@Data
public class ComponentInfo {
    private String name;
    private boolean hasDesignDialog;
}
