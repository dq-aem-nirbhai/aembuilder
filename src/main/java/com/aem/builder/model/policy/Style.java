package com.aem.builder.model.policy;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Style {
    private String name;
    private String className;
    private String title;
    private boolean defaultStyle;
}
