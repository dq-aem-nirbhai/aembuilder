package com.aem.builder.model;

import lombok.Data;
import lombok.Getter;
import lombok.Setter;

@Data
@Getter
@Setter
public class FieldData {

    private String name;
    private String type;

    public FieldData(String name, String type) {
        this.name = name;
        this.type = type;
    }


}