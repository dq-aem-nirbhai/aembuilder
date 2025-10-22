package com.aem.builder.slingModels;

import java.util.List;

import org.apache.sling.api.resource.Resource;
import org.apache.sling.models.annotations.DefaultInjectionStrategy;
import org.apache.sling.models.annotations.Model;
import org.apache.sling.models.annotations.injectorspecific.ValueMapValue;
import org.apache.sling.models.annotations.injectorspecific.ChildResource;

@Model(adaptables = Resource.class, defaultInjectionStrategy = DefaultInjectionStrategy.OPTIONAL)
public class Navitems {

    @ValueMapValue
    private String itemname;

    public String getItemname() {
        return itemname;
    }

    @ValueMapValue
    private String itemlink;

    public String getItemlink() {
        return itemlink;
    }

}