package com.aem.builder.slingModels;


import org.apache.sling.api.resource.Resource;
import org.apache.sling.models.annotations.DefaultInjectionStrategy;
import org.apache.sling.models.annotations.Model;
import org.apache.sling.models.annotations.injectorspecific.ChildResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

@Model(adaptables = Resource.class, defaultInjectionStrategy = DefaultInjectionStrategy.OPTIONAL)
public class CardsModel {

    private static final Logger LOG = LoggerFactory.getLogger(CardsModel.class);

    @ChildResource
    private List<CardsItemsModel> cards;


    public List<CardsItemsModel> getCards() {
        LOG.info("Fetching card list. Total cards: {}", cards != null ? cards.size() : 0);
        return cards;
    }

    public boolean isEmpty() {
        boolean isEmpty = cards == null || cards.isEmpty();
        LOG.info("Checking if the component is empty: {}", isEmpty);
        return isEmpty;
    }
}