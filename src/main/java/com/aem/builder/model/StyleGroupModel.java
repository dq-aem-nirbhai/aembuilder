package com.aem.builder.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class StyleGroupModel {
    private String name;
    private boolean combine;
    private List<StyleModel> styles = new ArrayList<>();
}
