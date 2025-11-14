package com.aem.builder.model;

import lombok.Data;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;
@Data
@Getter
@Setter
public class TabData {

    private String name;
    private List<FieldData> fields = new ArrayList<>();

    public TabData(String name) {
        this.name = name;
    }

    public void addField(FieldData field) {
        this.fields.add(field);
    }
    public void addSelectOptions(String fieldName, List<String> options) {
        fields.stream()
                .filter(f -> f.getName().equals(fieldName))
                .findFirst()
                .ifPresent(f -> f.setSelectOptions(options));
    }

    public void addMultiFieldItems(String fieldName, List<String> items) {
        fields.stream()
                .filter(f -> f.getName().equals(fieldName))
                .findFirst()
                .ifPresent(f -> f.setMultiFieldItems(items));
    }


}