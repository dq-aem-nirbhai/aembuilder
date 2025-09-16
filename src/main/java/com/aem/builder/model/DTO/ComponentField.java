package com.aem.builder.model.DTO;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Represents a single dialog field definition.
 */

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ComponentField {
    private String fieldLabel;                 // e.g., Title
    private String fieldName;                  // e.g., title
    private String fieldType;                  // from FieldType enum
    @JsonProperty("isParentTab")
    private boolean isParentTab;
    private List<ComponentField> nestedFields; // only for multifields
    private List<OptionItem> options;          // for select/checkboxgroup values

}