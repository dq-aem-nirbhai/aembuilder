package com.aem.builder.slingModels;

import org.apache.sling.api.resource.Resource;
import org.apache.sling.models.annotations.DefaultInjectionStrategy;
import org.apache.sling.models.annotations.Model;
import org.apache.sling.models.annotations.injectorspecific.ValueMapValue;
import org.apache.sling.models.annotations.injectorspecific.ChildResource;

@Model(adaptables = Resource.class, defaultInjectionStrategy = DefaultInjectionStrategy.OPTIONAL)
public class Footerlinks {

    @ValueMapValue
    private String footername;

    public String getFootername() {
        return footername;
    }

    @ValueMapValue
    private String footerlink;

    public String getFooterlink() {
        return footerlink;
    }

}