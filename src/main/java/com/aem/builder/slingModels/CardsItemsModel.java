package com.aem.builder.slingModels;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.models.annotations.DefaultInjectionStrategy;
import org.apache.sling.models.annotations.Model;
import org.apache.sling.models.annotations.injectorspecific.ValueMapValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Sling Model for a single Card item.
 * Maps and exposes properties like image, title, description, and showButton.
 *
 * No interface used — directly accessed via getter methods.
 *
 * @author
 * @version 1.1
 * @since 2025-07-25
 */
@Model(adaptables = Resource.class, defaultInjectionStrategy = DefaultInjectionStrategy.OPTIONAL)
public class CardsItemsModel {

    private static final Logger LOG = LoggerFactory.getLogger(CardsItemsModel.class);

    @ValueMapValue
    private String fileReference;

    @ValueMapValue
    private String title;

    @ValueMapValue
    private String description;

    @ValueMapValue
    private boolean showButton;

    public String getFileReference() {
        LOG.info("Getting fileReference: {}", fileReference);
        return fileReference;
    }

    public String getTitle() {
        LOG.info("Getting title: {}", title);
        return title;
    }

    public String getDescription() {
        LOG.info("Getting description: {}", description);
        return description;
    }

    public boolean getShowButton() {
        LOG.info("Show button flag: {}", showButton);
        return showButton;
    }

    public boolean isDescriptionEmpty() {
        boolean isEmpty = description == null || description.trim().isEmpty();
        LOG.info("Checking if description is empty: {}", isEmpty);
        return isEmpty;
    }
}
