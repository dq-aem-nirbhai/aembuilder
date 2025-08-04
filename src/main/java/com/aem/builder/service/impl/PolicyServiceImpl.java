package com.aem.builder.service.impl;

import com.aem.builder.model.PolicyModel;
import com.aem.builder.model.StyleGroupModel;
import com.aem.builder.model.StyleModel;
import com.aem.builder.service.PolicyService;
import org.springframework.stereotype.Service;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

@Service
public class PolicyServiceImpl implements PolicyService {

    @Override
    public PolicyModel getPolicy(String project, String template, String component) {
        Path file = getPolicyFile(project, template, component);
        if (Files.notExists(file)) {
            return new PolicyModel();
        }
        try {
            Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file.toFile());
            Element root = doc.getDocumentElement();
            PolicyModel policy = new PolicyModel();
            policy.setName(root.getAttribute("name"));
            policy.setTitle(root.getAttribute("title"));
            policy.setDescription(root.getAttribute("description"));
            policy.setDefaultCssClass(root.getAttribute("defaultCssClass"));
            NodeList groupNodes = root.getElementsByTagName("styleGroup");
            for (int i = 0; i < groupNodes.getLength(); i++) {
                Element gEl = (Element) groupNodes.item(i);
                StyleGroupModel group = new StyleGroupModel();
                group.setName(gEl.getAttribute("name"));
                group.setCombine(Boolean.parseBoolean(gEl.getAttribute("combine")));
                NodeList styleNodes = gEl.getElementsByTagName("style");
                ArrayList<StyleModel> styles = new ArrayList<>();
                for (int j = 0; j < styleNodes.getLength(); j++) {
                    Element sEl = (Element) styleNodes.item(j);
                    styles.add(new StyleModel(sEl.getAttribute("name"), sEl.getAttribute("cssClass")));
                }
                group.setStyles(styles);
                policy.getStyleGroups().add(group);
            }
            return policy;
        } catch (Exception e) {
            throw new RuntimeException("Failed to read policy", e);
        }
    }

    @Override
    public void savePolicy(String project, String template, String component, PolicyModel policy) {
        Path file = getPolicyFile(project, template, component);
        try {
            Files.createDirectories(file.getParent());
            String xml = policyToXml(policy);
            Files.writeString(file, xml, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException e) {
            throw new RuntimeException("Failed to save policy", e);
        }
    }

    private Path getPolicyFile(String project, String template, String component) {
        return Paths.get("src/main/resources/conf", project, "settings", "wcm", "policies", template, "policies", component, ".content.xml");
    }

    private String policyToXml(PolicyModel policy) {
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        sb.append("<policy");
        appendAttr(sb, "name", policy.getName());
        appendAttr(sb, "title", policy.getTitle());
        appendAttr(sb, "description", policy.getDescription());
        appendAttr(sb, "defaultCssClass", policy.getDefaultCssClass());
        sb.append(">\n");
        if (policy.getStyleGroups() != null) {
            for (StyleGroupModel group : policy.getStyleGroups()) {
                sb.append("  <styleGroup");
                appendAttr(sb, "name", group.getName());
                appendAttr(sb, "combine", String.valueOf(group.isCombine()));
                sb.append(">\n");
                if (group.getStyles() != null) {
                    for (StyleModel style : group.getStyles()) {
                        sb.append("    <style");
                        appendAttr(sb, "name", style.getName());
                        appendAttr(sb, "cssClass", style.getCssClass());
                        sb.append("/>\n");
                    }
                }
                sb.append("  </styleGroup>\n");
            }
        }
        sb.append("</policy>\n");
        return sb.toString();
    }

    private void appendAttr(StringBuilder sb, String name, String value) {
        if (value != null && !value.isEmpty()) {
            sb.append(' ').append(name).append("=\"").append(escape(value)).append('\"');
        }
    }

    private String escape(String value) {
        return value.replace("&", "&amp;").replace("\"", "&quot;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
