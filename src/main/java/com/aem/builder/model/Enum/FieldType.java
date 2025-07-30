package com.aem.builder.model.Enum;

import java.util.Map;

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
    TAGFIELD("tagfield", "cq/gui/components/coral/common/form/tagfield"),
    TABS("tabs", "granite/ui/components/coral/foundation/tabs"),
    NESTED_MULTIFIELD("nestedmultifield", "granite/ui/components/coral/foundation/form/multifield");

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
        return Map.ofEntries(
                TEXTFIELD.toEntry(), TEXTAREA.toEntry(), NUMBERFIELD.toEntry(), HIDDEN.toEntry(),
                CHECKBOX.toEntry(), CHECKBOXGROUP.toEntry(), RADIOGROUP.toEntry(), SELECT.toEntry(),
                MULTISELECT.toEntry(), PATHFIELD.toEntry(), AUTOCOMPLETE.toEntry(), DATEPICKER.toEntry(),
                IMAGE.toEntry(), FILEUPLOAD.toEntry(), RICHTEXT.toEntry(), MULTIFIELD.toEntry(),
                BUTTON.toEntry(), PASSWORD.toEntry(), SWITCH.toEntry(), ANCHORBROWSER.toEntry(),
                TAGFIELD.toEntry(), TABS.toEntry(), NESTED_MULTIFIELD.toEntry()
        );
    }

    private Map.Entry<String, String> toEntry() {
        return Map.entry(this.type, this.resourceType);
    }
}
