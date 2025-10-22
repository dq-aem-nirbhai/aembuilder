package com.aem.builder.slingModels;

import java.util.List;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.models.annotations.DefaultInjectionStrategy;
import org.apache.sling.models.annotations.Model;
import org.apache.sling.models.annotations.injectorspecific.ValueMapValue;
import org.apache.sling.models.annotations.injectorspecific.ChildResource;

@Model(adaptables = Resource.class, defaultInjectionStrategy = DefaultInjectionStrategy.OPTIONAL)
public class Footer2Model {

    @ValueMapValue
    private String maintitle;

    public String getMaintitle() {
        return maintitle;
    }

    @ChildResource
    private List<Footerlinks> footerlinks;

    public List<Footerlinks> getFooterlinks() {
        return footerlinks;
    }

    @ChildResource
    private List<Socialmediaicons> socialmediaicons;

    public List<Socialmediaicons> getSocialmediaicons() {
        return socialmediaicons;
    }

    /**
 * Checks if all fields in this model are empty.
 * Used in HTL: ${!model.empty}
 */
    public boolean isEmpty() {
        boolean empty = true;
        if (maintitle != null && !maintitle.isEmpty()) empty = false;
        if (footerlinks != null && !footerlinks.isEmpty()) empty = false;
        if (socialmediaicons != null && !socialmediaicons.isEmpty()) empty = false;
        return empty;
    }
}