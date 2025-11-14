package com.aem.builder.model;

import lombok.Data;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Data
@Getter
@Setter
public class FieldData {

    private String name;
    private String type;
    private List<String> selectOptions;
    private List<String> multiFieldItems;

    public void setSelectOptions(List<String> options) {
        this.selectOptions = options;
    }

    public void setMultiFieldItems(List<String> items) {
        this.multiFieldItems = items;
    }

    public FieldData(String name, String type) {
        this.name = name;
        this.type = type;
    }

}