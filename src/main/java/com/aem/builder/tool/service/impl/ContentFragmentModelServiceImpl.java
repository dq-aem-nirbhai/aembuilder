package com.aem.builder.tool.service.impl;


import com.aem.builder.tool.service.ContentFragmentModelService;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ValueMap;
import org.osgi.service.component.annotations.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


@Component(service = ContentFragmentModelService.class, immediate = true)
public class ContentFragmentModelServiceImpl implements ContentFragmentModelService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ContentFragmentModelServiceImpl.class);
    private static final String CF_MODELS_BASE_PATH = "/conf/charger/settings/dam/cfm/models";

    @Override
    public List<Map<String, String>> getAllModels(ResourceResolver resolver) {
        List<Map<String, String>> models = new ArrayList<>();

        Resource baseResource = resolver.getResource(CF_MODELS_BASE_PATH);
        if (baseResource == null) {
            LOGGER.warn("No models folder found at path: {}", CF_MODELS_BASE_PATH);
            return models;
        }

        for (Resource model : baseResource.getChildren()) {
            // 🚫 Skip system/internal nodes like jcr:content
            if ("jcr:content".equalsIgnoreCase(model.getName())) {
                continue;
            }

            Map<String, String> modelInfo = new HashMap<>();
            modelInfo.put("name", model.getName());
            modelInfo.put("path", model.getPath());

            // ✅ Try to get model title from jcr:content
            Resource content = model.getChild("jcr:content");
            if (content != null) {
                ValueMap contentVM = content.getValueMap();
                String title = contentVM.get("jcr:title", model.getName());
                modelInfo.put("title", title);
            } else {
                modelInfo.put("title", model.getName());
            }

            models.add(modelInfo);
        }

        LOGGER.info("Found {} content fragment models", models.size());
        return models;
    }



}
