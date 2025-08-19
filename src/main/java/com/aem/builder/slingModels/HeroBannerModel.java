package com.aem.builder.slingModels;

import org.apache.sling.api.resource.Resource;
import org.apache.sling.models.annotations.DefaultInjectionStrategy;
import org.apache.sling.models.annotations.Model;
import org.apache.sling.models.annotations.injectorspecific.ValueMapValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Mohammad Shoaib
 * @version v1.0
 * @since 20-05-2025
 * <p>
 * Implementation class for the HeroBanner Sling Model.
 * <p>
 * This model is adapted from a Sling Resource and is used to expose
 * content properties for the HeroBanner AEM component.
 * </p>
 *
 * <p>
 * The fields such as title, details, and image are injected from the
 * resource's value map and made available via getter methods.
 * </p>
 */
@Model(
        adaptables = Resource.class,
        defaultInjectionStrategy = DefaultInjectionStrategy.OPTIONAL
)
public class HeroBannerModel {
    private static final Logger log = LoggerFactory.getLogger(HeroBannerModel.class);

    /**
     * The title text for the Hero Banner.
     */
    @ValueMapValue
    private String title;

    /**
     * The detailed description text for the Hero Banner.
     */
    @ValueMapValue
    private String details;

    /**
     * The image path or URL used in the Hero Banner.
     */
    @ValueMapValue
    private String image;

    /**
     * Returns the title of the Hero Banner component.
     *
     * @return the title text
     */
    public String getTitle() {
        log.info("Getting Title of HeroBanner component");
        return title;
    }

    /**
     * Returns the details/description of the Hero Banner component.
     *
     * @return the detailed text
     */
    public String getDetails() {
        log.info("Getting Details of HeroBanner component");
        return details;
    }

    /**
     * Returns the image path or URL associated with the Hero Banner.
     *
     * @return the image source
     */
    public String getImage() {
        log.info("Getting Image of HeroBanner component");
        return image;
    }

    /**
     * Checks whether the Hero Banner component has no content.
     * <p>
     * This method evaluates if all key fields (title, details, and image)
     * are either {@code null} or empty strings. It is useful in determining
     * whether the component should be rendered on a page or skipped.
     * </p>
     *
     * @return {@code true} if all fields are null or empty,
     * {@code false} if at least one field has a value.
     */
    public boolean isEmpty() {
        boolean isEmpty = (title == null || title.isEmpty()) && (details == null || details.isEmpty())
                && (image == null || image.isEmpty());
        log.info("Checking if the components is empty: {}", isEmpty);
        return isEmpty;
    }
}