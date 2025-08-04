package com.aem.builder.service;

import com.aem.builder.model.*;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class PolicyService {
    private final Map<String, Template> templates = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        Style defaultStyle = new Style("Default", "cmp-default");
        StyleGroup group = new StyleGroup("Basic", new ArrayList<>(List.of(defaultStyle)));
        Policy textPolicy = new Policy(UUID.randomUUID().toString(), "text-default", "Text Policy", "cmp-text", new ArrayList<>(List.of(group)));
        Component textComponent = new Component("text", "Text", new ArrayList<>(List.of(textPolicy)));
        Template template = new Template("template1", "Example Template", new ArrayList<>(List.of(textComponent)));
        templates.put(template.getId(), template);
    }

    public List<Template> getTemplates() {
        return new ArrayList<>(templates.values());
    }

    public Template getTemplate(String id) {
        return templates.get(id);
    }

    public List<Component> getComponents(String templateId) {
        Template template = getTemplate(templateId);
        return template != null ? template.getComponents() : Collections.emptyList();
    }

    public Component getComponent(String templateId, String componentId) {
        return getComponents(templateId).stream().filter(c -> c.getId().equals(componentId)).findFirst().orElse(null);
    }

    public List<Policy> getPolicies(String templateId, String componentId) {
        Component component = getComponent(templateId, componentId);
        return component != null ? component.getPolicies() : Collections.emptyList();
    }

    public Policy getPolicy(String templateId, String componentId, String policyId) {
        return getPolicies(templateId, componentId).stream().filter(p -> p.getId().equals(policyId)).findFirst().orElse(null);
    }

    public Policy savePolicy(String templateId, String componentId, Policy policy) {
        Component component = getComponent(templateId, componentId);
        if (component == null) return null;
        if (policy.getId() == null || policy.getId().isEmpty()) {
            policy.setId(UUID.randomUUID().toString());
            component.getPolicies().add(policy);
        } else {
            component.getPolicies().removeIf(p -> p.getId().equals(policy.getId()));
            component.getPolicies().add(policy);
        }
        return policy;
    }
}
