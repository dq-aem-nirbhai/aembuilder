package com.aem.builder.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class StyleGroup {
    private String name;
    private boolean combine;
    private List<StylePolicy> styles = new ArrayList<>();
}
