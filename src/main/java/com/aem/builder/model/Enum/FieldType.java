package com.aem.builder.model.Enum;

import java.util.Map;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.stream.Collectors;

public enum FieldType {
    TEXTFIELD("textfield", "granite/ui/components/coral/foundation/form/textfield"),
    TEXTAREA("textarea", "granite/ui/components/coral/foundation/form/textarea"),
    NUMBERFIELD("numberfield", "granite/ui/components/coral/foundation/form/numberfield"),
    HIDDEN("hidden", "granite/ui/components/coral/foundation/form/hidden"),
    CHECKBOX("checkbox", "granite/ui/components/coral/foundation/form/checkbox"),
    CHECKBOXGROUP("checkboxgroup", "granite/ui/components/coral/foundation/form/checkboxgroup"),
    RADIOGROUP("radiogroup", "granite/ui/components/coral/foundation/form/radiogroup"),
    SELECT("select", "granite/ui/components/coral/foundation/form/select"),
    MULTISELECT("multiselect", "granite/ui/components/coral/foundation/form/multifield"),
    PATHFIELD("pathfield", "granite/ui/components/coral/foundation/form/pathfield"),
    AUTOCOMPLETE("autocomplete", "granite/ui/components/coral/foundation/form/autocomplete"),
    DATEPICKER("datepicker", "granite/ui/components/coral/foundation/form/datepicker"),
    IMAGE("image", "cq/gui/components/authoring/dialog/fileupload"),
    FILEUPLOAD("fileupload", "cq/gui/components/authoring/dialog/fileupload"),
    RICHTEXT("richtext", "cq/gui/components/authoring/dialog/richtext"),
    MULTIFIELD("multifield", "granite/ui/components/coral/foundation/form/multifield"),
    BUTTON("button", "granite/ui/components/coral/foundation/form/button"),
    PASSWORD("password", "granite/ui/components/coral/foundation/form/password"),
    SWITCH("switch", "granite/ui/components/coral/foundation/form/switch"),
    ANCHORBROWSER("anchorbrowser", "cq/gui/components/coral/common/form/anchorbrowser"),
    TAGFIELD("tagfield", "cq/gui/components/coral/common/form/tagfield");

    private final String type;
    private final String resourceType;

    FieldType(String type, String resourceType) {
        this.type = type;
        this.resourceType = resourceType;
    }

    public String getType() {
        return type;
    }

    public String getResourceType() {
        return resourceType;
    }

    public static Map<String, String> getTypeResourceMap() {
        return Arrays.stream(values())
                .sorted(Comparator.comparing(FieldType::getType))
                .collect(Collectors.toMap(
                        FieldType::getType,
                        FieldType::getResourceType,
                        (a, b) -> a,
                        LinkedHashMap::new
                ));
    }

    private Map.Entry<String, String> toEntry() {
        return Map.entry(this.type, this.resourceType);
    }
}
