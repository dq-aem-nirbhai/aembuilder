package com.aem.builder.model.DTO;

import com.aem.builder.model.Enum.FieldType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

import com.aem.builder.model.DTO.OptionItem;

/**
 * Represents a single dialog field definition.
 */

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ComponentField {
    private String fieldName;                  // e.g., title
    private String fieldLabel;                 // e.g., Title
    private String fieldType;                  // from FieldType enum
    private List<ComponentField> nestedFields; // only for multifields
    private List<OptionItem> options;          // for select/checkboxgroup values

}