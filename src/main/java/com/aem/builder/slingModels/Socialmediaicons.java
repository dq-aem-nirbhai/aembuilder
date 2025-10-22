package com.aem.builder.slingModels;

import java.util.List;

import org.apache.sling.api.resource.Resource;
import org.apache.sling.models.annotations.DefaultInjectionStrategy;
import org.apache.sling.models.annotations.Model;
import org.apache.sling.models.annotations.injectorspecific.ValueMapValue;
import org.apache.sling.models.annotations.injectorspecific.ChildResource;

@Model(adaptables = Resource.class, defaultInjectionStrategy = DefaultInjectionStrategy.OPTIONAL)
public class Socialmediaicons {

    @ValueMapValue
    private String icon;

    public String getIcon() {
        return icon;
    }

    @ValueMapValue
    private String iconlink;

    public String getIconlink() {
        return iconlink;
    }

}