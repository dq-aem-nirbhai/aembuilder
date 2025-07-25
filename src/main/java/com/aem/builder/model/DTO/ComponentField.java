package com.aem.builder.model.DTO;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ComponentField {
    private String fieldName;
    private String fieldLabel;
    private String fieldType;

}