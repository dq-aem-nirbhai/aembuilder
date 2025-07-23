package com.aem.builder.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ProjectDetails {
    private String name;
    private String version;
    private String groupId;
    private String createdDate;
    private String path;
}
