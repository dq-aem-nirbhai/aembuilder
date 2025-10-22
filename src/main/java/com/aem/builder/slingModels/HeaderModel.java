package com.aem.builder.slingModels;

import java.util.List;

import org.apache.sling.api.resource.Resource;
import org.apache.sling.models.annotations.DefaultInjectionStrategy;
import org.apache.sling.models.annotations.Model;
import org.apache.sling.models.annotations.injectorspecific.ValueMapValue;
import org.apache.sling.models.annotations.injectorspecific.ChildResource;

@Model(adaptables = Resource.class, defaultInjectionStrategy = DefaultInjectionStrategy.OPTIONAL)
public class HeaderModel {

    @ValueMapValue
    private String brandimage;

    public String getBrandimage() {
        return brandimage;
    }

    @ValueMapValue
    private String brandlink;

    public String getBrandlink() {
        return brandlink;
    }

    @ChildResource
    private List<Navitems> navitems;

    public List<Navitems> getNavitems() {
        return navitems;
    }

    /**
 * Checks if all fields in this model are empty.
 * Used in HTL: ${!model.empty}
 */
    public boolean isEmpty() {
        boolean empty = true;
        if (brandimage != null && !brandimage.isEmpty()) empty = false;
        if (brandlink != null && !brandlink.isEmpty()) empty = false;
        if (navitems != null && !navitems.isEmpty()) empty = false;
        return empty;
    }
}