package com.aem.builder.service.impl;

import com.aem.builder.config.ConfigUtil;
import com.aem.builder.constants.PolicyConstants;
import com.aem.builder.model.ComponentInfo;
import com.aem.builder.model.PolicyModel;
import com.aem.builder.model.StyleGroupModel;
import com.aem.builder.model.StyleModel;
import com.aem.builder.service.PolicyService;
import com.aem.builder.util.FolderUtil;
import com.aem.builder.util.PathUtil;
import com.aem.builder.util.PolicyUtil;
import com.aem.builder.util.XmlUtil;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import javax.xml.parsers.DocumentBuilder;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import static com.aem.builder.constants.AemProjectConstants.PROJECTS_DIR;
import static com.aem.builder.constants.PolicyConstants.CONF_RELATIVE_PATH;
import static com.aem.builder.constants.PolicyConstants.SETTINGS_WCM_POLICY_PATH;
import static org.springframework.util.xml.DomUtils.getChildElements;

@Slf4j
@Service
public class PolicyServiceImpl implements PolicyService {

    private static final AtomicLong lastId = new AtomicLong(System.currentTimeMillis());

    @Autowired
    private ComponentServiceImpl componentService;
    /**
     * Creates a new DocumentBuilder instance using XML utility.
     *
     * @return DocumentBuilder instance
     */
    private DocumentBuilder newBuilder() {
        log.info("CREATE_DOCUMENT_BUILDER: Invoked");
        return XmlUtil.newDocumentBuilder();
    }

    /**
     * Builds the configuration path for a given project.
     *
     * @param project project name
     * @return full configuration path
     */
    private String buildConfPath(String project) {
        String path = PROJECTS_DIR + "/" + project +CONF_RELATIVE_PATH+ project;
        log.info("BUILD_CONF_PATH: {}", path); // method name as prefix
        return path;
    }

    /**
     * Gets allowed components for a template in a project.
     *
     * @param project  project name
     * @param template template name
     * @return list of allowed component names
     */
    @Override
    public List<String> getAllowedComponents(String project, String template) {
        final String METHOD = "GET_ALLOWED_COMPONENTS";
        try {
            // Step 1: Build template structure path
            String base = buildConfPath(project) + PolicyConstants.WCM_TEMPLATES_RELATIVE_PATH + template;
            log.info("{}: Template base path = {}", METHOD, base);

            File structureFile = new File(base + PolicyConstants.STRUCTURE_FILE);
            String containerPath = "root";

            if (structureFile.exists()) {
                Document doc = newBuilder().parse(structureFile);
                Element root = doc.getDocumentElement();
                Element content = getChild(root, "jcr:content");

                if (content != null) {
                    NodeList children = content.getChildNodes();
                    for (int i = 0; i < children.getLength(); i++) {
                        if (children.item(i) instanceof Element el) {
                            String found = findLayoutContainerPath(el, el.getTagName());
                            if (found != null) {
                                containerPath = found;
                                log.info("{}: Resolved containerPath='{}'", METHOD, containerPath);
                                break;
                            }
                        }
                    }
                }
            } else {
                log.info("{}: Template structure file does not exist: {}", METHOD, structureFile.getAbsolutePath());
            }

            // Step 2: Load policy mapping file
            File mappingFile = new File(base + PolicyConstants.POLICY_FILE);
            if (!mappingFile.exists()) {
                log.info("{}: Template policy mapping file not found: {}", METHOD, mappingFile.getAbsolutePath());
                return List.of();
            }

            Document mapDoc = newBuilder().parse(mappingFile);
            Element mappingRoot = getChild(mapDoc.getDocumentElement(), "jcr:content");
            if (mappingRoot == null) {
                log.info("{}: jcr:content node not found in policy mapping", METHOD);
                return List.of();
            }

            Element current = mappingRoot;
            for (String seg : containerPath.split("/")) {
                current = getChild(current, seg);
                if (current == null) {
                    log.info("{}: Container path '{}' segment '{}' not found in policy mapping", METHOD, containerPath, seg);
                    return List.of();
                }
            }

            String policyRelPath = current.getAttribute("cq:policy");
            log.info("{}: Resolved policyRelPath='{}' for containerPath='{}'", METHOD, policyRelPath, containerPath);

            if (policyRelPath == null || policyRelPath.isBlank()) {
                log.info("{}: No cq:policy attribute found at path '{}'", METHOD, containerPath);
                return List.of();
            }

            // Step 3: Return allowed components
            return getAllowedComponentsFromPolicy(project, policyRelPath);

        } catch (Exception e) {
            log.info("{}: Error getting allowed components for project: {}, template: {}", METHOD, project, template, e);
            return List.of();
        }
    }


    /**
     * Gets allowed components from a policy path.
     *
     * @param project       project name
     * @param policyRelPath relative path to the policy
     * @return list of allowed component names
     */
    @Override
    public List<String>  getAllowedComponentsFromPolicy(String project, String policyRelPath) {
        final String METHOD = "GET_ALLOWED_COMPONENTS_FROM_POLICY";
        try {
            File policyFile = new File(buildConfPath(project) +SETTINGS_WCM_POLICY_PATH);
            if (!policyFile.exists()) {
                log.info("{}: Policy file does not exist: {}", METHOD, policyFile.getAbsolutePath());
                return List.of();
            }

            Document doc = newBuilder().parse(policyFile);
            Element current = doc.getDocumentElement();

            for (String seg : policyRelPath.split("/")) {
                current = getChild(current, seg);
                if (current == null) {
                    log.info("{}: Segment '{}' not found in policy path '{}'", METHOD, seg, policyRelPath);
                    return List.of();
                }
            }

            String raw = current.getAttribute("components");
            if (raw == null || raw.isBlank()) raw = current.getAttribute("cq:allowedComponents");
            if (raw == null || raw.isBlank()) {
                log.info("{}: No components or cq:allowedComponents found at '{}'", METHOD, policyRelPath);
                return List.of();
            }

            List<String> result = new ArrayList<>();
            String[] entries = raw.replace("[", "").replace("]", "").split(",");

            for (String entry : entries) {
                entry = entry.trim();
                if (entry.startsWith("group:")) {
                    String groupName = entry.substring("group:".length()).trim();
                    List<String> groupComponents = resolveComponentsByGroup(project, groupName);
                    for (String comp : groupComponents) {
                        result.add(getComponentNameOnly(comp));
                    }
                } else if (!entry.isEmpty()) {
                    result.add(getComponentNameOnly(entry));
                }
            }

            log.info("{}: Resolved {} allowed components for policy path '{}'", METHOD, result.size(), policyRelPath);
            return result;

        } catch (Exception e) {
            log.info("{}: Error getting allowed components from policy for project: {}, path: {}", METHOD, project, policyRelPath, e);
            return List.of();
        }
    }


    /**
     * Extracts the component name from a path.
     *
     * @param path full component path
     * @return the component name (last segment of path), or null/empty if input invalid
     */
    @Override
    public String getComponentNameOnly(String path) {
        return PolicyUtil.getComponentNameOnly(path);
    }


    /**
     * Resolves all components belonging to a group.
     *
     * @param project   project name
     * @param groupName component group name
     * @return list of component names in the group
     */
    @Override
    public List<String> resolveComponentsByGroup(String project, String groupName) {
        final String METHOD = "RESOLVE_COMPONENTS_BY_GROUP";
        List<String> components = new ArrayList<>();

        File baseDir = new File(
                PROJECTS_DIR + "/" + project
                        + PolicyConstants.COMPONENTS_PATH_RELATIVE
        );

        if (!baseDir.exists()) {
            log.info("{}: Component base directory does not exist: {}", METHOD, baseDir.getAbsolutePath());
            return components;
        }

        try {
            Files.walk(baseDir.toPath())
                    .filter(path -> path.getFileName().toString().equals(".content.xml"))
                    .forEach(path -> {
                        try {
                            Document doc = newBuilder().parse(path.toFile());
                            Element root = doc.getDocumentElement();
                            String groupAttr = root.getAttribute("componentGroup");

                            if (groupAttr != null && groupAttr.equalsIgnoreCase(groupName)) {
                                Path componentDir = path.getParent();
                                String componentName = componentDir.getFileName().toString();
                                components.add(componentName);
                                log.info("{}: Found component '{}' in group '{}'", METHOD, componentName, groupName);
                            }
                        } catch (Exception ex) {
                            log.info("{}: Error resolving component group '{}': {}", METHOD, groupName, ex.getMessage());
                        }
                    });
        } catch (IOException e) {
            log.info("{}: Error walking component directories for group '{}'", METHOD, groupName, e);
        }

        log.info("{}: Total {} components found for group '{}'", METHOD, components.size(), groupName);
        return components;
    }


    /**
     * Recursively finds the layout container path in the template structure.
     *
     * @param element current XML element
     * @param path    accumulated path to this element
     * @return path of the editable container if found, otherwise non-editable fallback or null
     */
    private String findLayoutContainerPath(Element element, String path) {
        final String METHOD = "FIND_LAYOUT_CONTAINER_PATH";

        String resourceType = element.getAttribute("sling:resourceType");
        boolean isContainer = resourceType != null && resourceType.contains("container");
        boolean editable = element.hasAttribute("editable") && element.getAttribute("editable").contains("{Boolean}true");

        log.info("{}: Checking element '{}', path='{}', resourceType='{}', isContainer={}, editable={}",
                METHOD, element.getTagName(), path, resourceType, isContainer, editable);

        // If current element is editable container, return immediately
        if (isContainer && editable) {
            log.info("{}: Editable container found at path='{}'", METHOD, path);
            return path;
        }

        NodeList children = element.getChildNodes();
        String fallback = null; // store first non-editable container as fallback

        // Recurse into children first
        for (int i = 0; i < children.getLength(); i++) {
            if (children.item(i) instanceof Element child) {
                String childPath = path + "/" + child.getTagName();
                String found = findLayoutContainerPath(child, childPath);

                // If editable container found in subtree, return immediately
                if (found != null) {
                    if (!found.equals(childPath) || (child.hasAttribute("editable") && child.getAttribute("editable").contains("{Boolean}true"))) {
                        return found;
                    }
                }

                // If child is non-editable container and fallback not set yet
                String childResourceType = child.getAttribute("sling:resourceType");
                boolean childIsContainer = childResourceType != null && childResourceType.contains("container");
                boolean childEditable = child.hasAttribute("editable") && child.getAttribute("editable").contains("{Boolean}true");

                if (childIsContainer && !childEditable && fallback == null) {
                    fallback = childPath;
                    log.info("{}: Non-editable container found at path='{}', storing as fallback", METHOD, fallback);
                }
            }
        }

        // If no editable container found, use fallback from children
        if (fallback != null) {
            log.info("{}: Returning fallback container from children at path='{}'", METHOD, fallback);
            return fallback;
        }

        // If current element is non-editable container and no child fallback exists, return current element
        if (isContainer && !editable) {
            log.info("{}: Returning current non-editable container as fallback at path='{}'", METHOD, path);
            return path;
        }

        // Nothing found
        log.info("{}: No container found for element '{}', path='{}'", METHOD, element.getTagName(), path);
        return null;
    }



    /**
     * Gets a direct child element by name.
     */
    private Element getChild(Element parent, String name) {
       return PolicyUtil.getChild( parent,  name);
    }

    /**
     * Checks if design dialogs exist for a list of components in a project.
     * <p>
     * This method iterates over the provided components, verifies if the
     * corresponding "_cq_design_dialog/.content.xml" exists for each component,
     * then checks the same for core components using `checkDesignDialogsForCore`.
     * The final list preserves insertion order and updates components with found design dialogs.
     *
     * @param project    the project name
     * @param components the list of component names to check
     * @return a list of ComponentInfo objects with design dialog existence information
     */
    public List<ComponentInfo> checkDesignDialogs(String project, List<String> components) {
        final String METHOD = "CHECK_DESIGN_DIALOGS";

        // Early return if input list is null or empty
        if (components == null || components.isEmpty()) {
            log.info("{}: No components provided for project '{}'", METHOD, project);
            return List.of();
        }

        log.info("{}: Starting design dialog check for {} components in project '{}'", METHOD, components.size(), project);

        List<ComponentInfo> result = new ArrayList<>();

        // Step 1: Check each component for its design dialog file
        for (String component : components) {
            // Build the design dialog path using utility/service and constant
            String componentPath = componentService.findComponentPathExact(project, component);
            String designDialogPath = componentPath + PolicyConstants.DESIGN_DIALOG_PATH;

            File file = new File(designDialogPath);
            boolean exists = file.exists();

            log.info("{}: Component '{}' design dialog exists? {}", METHOD, component, exists);

            result.add(new ComponentInfo(component, exists));
        }

        // Step 2: Filter components that do not have a design dialog
        List<ComponentInfo> missingDialogs = result.stream()
                .filter(c -> !c.isHasDesignDialog())
                .toList();

        // Step 3: Check core components for design dialogs
        List<ComponentInfo> coreResults = checkDesignDialogsForCore(missingDialogs);

        // Step 4: Merge original results with core component results
        Map<String, ComponentInfo> merged = new LinkedHashMap<>();
        result.forEach(ci -> merged.put(ci.getName(), ci));
        coreResults.forEach(ci -> {
            if (ci.isHasDesignDialog()) {
                merged.put(ci.getName(), ci);
            }
        });

        log.info("{}: Completed design dialog check for project '{}'", METHOD, project);

        return new ArrayList<>(merged.values());
    }


    /**
     * Checks if design dialogs exist for a list of core/proxy components.
     * <p>
     * This method recursively searches the project's resources directory
     * for each component and updates its design dialog existence status.
     *
     * @param components list of ComponentInfo objects to check
     * @return list of ComponentInfo objects with updated design dialog flags
     */
    public List<ComponentInfo> checkDesignDialogsForCore(List<ComponentInfo> components) {
        final String METHOD = "CHECK_DESIGN_DIALOGS_FOR_CORE";

        if (components == null || components.isEmpty()) {
            log.info("{}: No core components provided to check", METHOD);
            return List.of();
        }

        // Step 1: Determine project resources directory
        Path projectDir = Paths.get(System.getProperty("user.dir"));
        File resourcesDir = projectDir.resolve("src/main/resources").toAbsolutePath().toFile();
        log.info("{}: Searching components under resources directory '{}'", METHOD, resourcesDir);

        // Step 2: Pre-search all 'dsc' components (optional initial search)
        componentService.searchComponentRecursiveExact(resourcesDir, "dsc");

        // Step 3: Map each component to updated ComponentInfo
        List<ComponentInfo> updatedComponents = components.stream()
                .map(ci -> {
                    String found = componentService.searchComponentRecursiveExact(resourcesDir, ci.getName());
                    boolean hasDesignDialog = (found != null);
                    log.info("{}: Component '{}' has design dialog? {}", METHOD, ci.getName(), hasDesignDialog);
                    return new ComponentInfo(ci.getName(), hasDesignDialog);
                })
                .collect(Collectors.toList());

        log.info("{}: Completed design dialog check for core components", METHOD);
        return updatedComponents;
    }

    /**
     * Saves a policy for a specific component in a given template.
     * <p>
     * This method:
     * 1. Ensures the central policies folder exists.
     * 2. Computes a unique policy ID if not provided.
     * 3. Determines the component's resource type path.
     * 4. Writes the policy file in the central policies location.
     * 5. Updates the template's policy mapping.
     *
     * @param project       the project name
     * @param template      the template name
     * @param componentName the component name
     * @param policy        the PolicyModel object containing policy details
     * @return the policy ID used to save the policy
     */
    @Override
    public String savePolicy(String project, String template, String componentName, PolicyModel policy) {
        final String METHOD = "SAVE_POLICY";

        // Step 1: Ensure policy has a unique ID
        String policyId = policy.getId();
        if (policyId == null || policyId.isBlank()) {
            policyId = "policy_" + System.currentTimeMillis();
            policy.setId(policyId);
            log.info("{}: Generated new policyId '{}'", METHOD, policyId);
        } else {
            log.info("{}: Using existing policyId '{}'", METHOD, policyId);
        }

        // Step 2: Compute base configuration path
        String base = buildConfPath(project);

        // Step 3: Ensure policies folder exists
        File policiesDir = new File(base + PolicyConstants.POLICIES_RELATIVE_PATH); // "/settings/wcm/policies"
       FolderUtil. ensureFolderContent(policiesDir);
        File centralPolicyFile = new File(policiesDir, ".content.xml");

        // Step 4: Compute component resource type path
        String fullPath = componentService.findComponentPathExact(project, componentName);
        String resourcePath = PathUtil.getResourceTypeFromPath(fullPath);

        // Step 5: Full path for the policy including policyId
        String fullPolicyPath = resourcePath + "/" + policyId;

        // Step 6: Write policy to central file
        writePolicyFile(centralPolicyFile, fullPolicyPath, policy);
        log.info("{}: Policy written to '{}'", METHOD, centralPolicyFile.getAbsolutePath());

        // Step 7: Update template's policy mapping
        String mappingPath = base + PolicyConstants.WCM_TEMPLATES_RELATIVE_PATH + template + "/policies/.content.xml";
        updateTemplateMapping(mappingPath, fullPolicyPath);
        log.info("{}: Updated template mapping at '{}'", METHOD, mappingPath);

        log.info("{}: Successfully saved policy '{}' for component '{}' at '{}'",
                METHOD, policyId, componentName, fullPolicyPath);

        return policyId;
    }


    /**
     * Writes or updates a policy in the central policy file.
     * <p>
     * Uses JSON configuration for JCR attributes, node names, and resource types.
     * Handles creation of new central policy file if not exists, path traversal,
     * clearing old content, and adding style groups dynamically.
     *
     * @param centralPolicyFile File object pointing to central policy XML
     * @param fullPolicyPath    Full path of the policy in the JCR structure
     * @param policy            PolicyModel object containing policy data
     */
    private void writePolicyFile(File centralPolicyFile, String fullPolicyPath, PolicyModel policy) {
        final String METHOD = "WRITE_POLICY_FILE";

        try {
            // Load JSON configuration for JCR nodes and attributes
            JsonNode config = ConfigUtil.getPolicyConfig();
            log.info("{}: Loaded JSON config for policy writing", METHOD);

            Document doc;
            Element root;

            JsonNode jcrConfig = config.get("jcr");
            String rootNodeName = jcrConfig.get("rootNode").asText();
            String primaryType = jcrConfig.get("primaryType").asText();
            JsonNode xmlns = jcrConfig.get("xmlns");

            // Load existing XML or create new document
            if (centralPolicyFile.exists()) {
                doc = newBuilder().parse(centralPolicyFile);
                root = doc.getDocumentElement();
                log.info("{}: Loaded existing central policy file '{}'", METHOD, centralPolicyFile.getAbsolutePath());
            } else {
                doc = newBuilder().newDocument();
                root = doc.createElement(rootNodeName);
                root.setAttribute("xmlns:jcr", xmlns.get("jcr").asText());
                root.setAttribute("xmlns:cq", xmlns.get("cq").asText());
                root.setAttribute("xmlns:sling", xmlns.get("sling").asText());
                root.setAttribute("xmlns:nt", xmlns.get("nt").asText());
                root.setAttribute(PolicyConstants.ATTR_PRIMARY_TYPE, primaryType);
                doc.appendChild(root);
                log.info("{}: Created new central policy file structure", METHOD);
            }

            // Traverse or create nodes along fullPolicyPath
            String[] segments = fullPolicyPath.split("/");
            Element current = root;
            for (String seg : segments) {
                if (seg.isBlank()) continue;

                Element child = PolicyUtil.findChild(current, seg, doc);
                if (child == null) {
                    child = doc.createElement(seg);
                    child.setAttribute(PolicyConstants.ATTR_PRIMARY_TYPE, primaryType);
                    current.appendChild(child);
                    log.info("{}: Created node '{}' under '{}'", METHOD, seg, current.getTagName());
                }
                current = child;
            }

            Element policyEl = current;

            // Clear old child nodes if updating
            while (policyEl.hasChildNodes()) {
                policyEl.removeChild(policyEl.getFirstChild());
            }
            log.info("{}: Cleared old child nodes for policy '{}'", METHOD, policy.getId());

            // Set main policy attributes
            JsonNode attr = config.get("attributes");
            JsonNode resourceTypes = config.get("resourceTypes");

            policyEl.setAttribute(PolicyConstants.ATTR_PRIMARY_TYPE, primaryType);
            policyEl.setAttribute(PolicyConstants.ATTR_TITLE, policy.getTitle());
            policyEl.setAttribute(PolicyConstants.ATTR_DESCRIPTION, policy.getDescription());
            policyEl.setAttribute(PolicyConstants.ATTR_STYLE_DEFAULT_CLASSES, policy.getDefaultCssClass());
            policyEl.setAttribute("sling:resourceType", PolicyConstants.POLICY_RESOURCE_TYPE);
            policyEl.setAttribute(PolicyConstants.ATTR_LAST_MODIFIED_BY, PolicyConstants.DEFAULT_LAST_MODIFIED_BY);
            policyEl.setAttribute(PolicyConstants.ATTR_LAST_MODIFIED, "{Date}" + PolicyUtil.getCurrentDate());
            log.info("{}: Set main policy attributes for '{}'", METHOD, policy.getId());

            // Add jcr:content node
            Element contentEl = doc.createElement(PolicyConstants.CONTENT_NODE);
            contentEl.setAttribute(PolicyConstants.ATTR_PRIMARY_TYPE, primaryType);
            policyEl.appendChild(contentEl);
            log.info("{}: Added '{}' node under policy '{}'", METHOD, PolicyConstants.CONTENT_NODE, policy.getId());

            // Add style groups if any
            if (!policy.getStyleGroups().isEmpty()) {
                Element styleGroupsEl = doc.createElement(PolicyConstants.STYLE_GROUPS_NODE);
                styleGroupsEl.setAttribute(PolicyConstants.ATTR_PRIMARY_TYPE, primaryType);

                for (int i = 0; i < policy.getStyleGroups().size(); i++) {
                    StyleGroupModel group = policy.getStyleGroups().get(i);

                    Element groupEl = doc.createElement("item" + i);
                    groupEl.setAttribute(PolicyConstants.ATTR_PRIMARY_TYPE, primaryType);
                    groupEl.setAttribute(PolicyConstants.ATTR_STYLE_GROUP_LABEL, group.getName());
                    groupEl.setAttribute(PolicyConstants.ATTR_STYLE_GROUP_MULTIPLE, String.valueOf(group.isAllowCombination()));

                    Element stylesEl = doc.createElement(PolicyConstants.STYLES_NODE);
                    stylesEl.setAttribute(PolicyConstants.ATTR_PRIMARY_TYPE, primaryType);

                    for (int j = 0; j < group.getStyles().size(); j++) {
                        StyleModel sm = group.getStyles().get(j);

                        Element styleEl = doc.createElement("item" + j);
                        styleEl.setAttribute(PolicyConstants.ATTR_PRIMARY_TYPE, primaryType);
                        styleEl.setAttribute(PolicyConstants.ATTR_STYLE_LABEL, sm.getName());
                        styleEl.setAttribute(PolicyConstants.ATTR_STYLE_CLASSES, sm.getCssClass());
                        styleEl.setAttribute(PolicyConstants.ATTR_STYLE_ID, String.valueOf(getUnique13DigitId()));
                        stylesEl.appendChild(styleEl);
                    }

                    groupEl.appendChild(stylesEl);
                    styleGroupsEl.appendChild(groupEl);
                }

                policyEl.appendChild(styleGroupsEl);
                log.info("{}: Added '{}' style groups for policy '{}'", METHOD, policy.getStyleGroups().size(), policy.getId());
            }

            // Save XML to file
           XmlUtil.writeDoc(doc, centralPolicyFile);
            log.info("{}: Policy '{}' written/updated successfully at '{}'", METHOD, policy.getId(), centralPolicyFile.getAbsolutePath());

        } catch (Exception e) {
            log.error("{}: Failed to write/update policy '{}' at '{}'", METHOD, policy.getId(),
                    centralPolicyFile != null ? centralPolicyFile.getAbsolutePath() : "null", e);
        }
    }



    /**
     * Retrieves all policies for a given component within a project.
     * <p>
     * Traverses the central policy XML file based on the component's resource type path,
     * parses each policy, and builds a list of PolicyModel objects including style groups.
     *
     * @param project       The project name
     * @param componentName The component name
     * @return List of PolicyModel objects representing policies for the component
     */
    @Override
    public List<PolicyModel> getPolicies(String project, String componentName) {
        final String METHOD = "GET_POLICIES";
        log.info("{}: Fetching policies for project: {}, component: {}", METHOD, project, componentName);

        List<PolicyModel> policies = new ArrayList<>();

        try {
            // Resolve policy file path
            File policyFile = new File(buildConfPath(project) + PolicyConstants.POLICY_FILE_RELATIVE_PATH);
            log.info("{}: Resolved policy file path: {}", METHOD, policyFile.getAbsolutePath());

            if (!policyFile.exists()) {
                log.warn("{}: Policy file does not exist: {}", METHOD, policyFile.getAbsolutePath());
                return policies;
            }

            // Parse XML document
            Document doc = newBuilder().parse(policyFile);
            Element root = doc.getDocumentElement();

            // Get component resource type path
            String fullPath = componentService.findComponentPathExact(project, componentName);
            String resourcePath = PathUtil.getResourceTypeFromPath(fullPath);
            String[] pathParts = resourcePath.split("/");

            // Traverse nodes according to resource path
            Element currentEl = root;
            for (String part : pathParts) {
                if (part.isEmpty()) continue;
                currentEl = getChild(currentEl, part);
                if (currentEl == null) {
                    log.warn("{}: Node not found for path segment: {}", METHOD, part);
                    return policies;
                }
            }

            // Iterate child nodes under the resolved component node
            Node policyNode = currentEl.getFirstChild();
            while (policyNode != null) {
                if (policyNode instanceof Element) {
                    Element policyEl = (Element) policyNode;

                    // Only process policy nodes
                    if (!PolicyConstants.POLICY_RESOURCE_TYPE.equals(policyEl.getAttribute("sling:resourceType"))) {
                        policyNode = policyNode.getNextSibling();
                        continue;
                    }

                    PolicyModel model = new PolicyModel();
                    model.setId(policyEl.getNodeName());
                    model.setTitle(policyEl.getAttribute(PolicyConstants.ATTR_TITLE));
                    model.setDescription(policyEl.getAttribute(PolicyConstants.ATTR_DESCRIPTION));
                    model.setDefaultCssClass(policyEl.getAttribute(PolicyConstants.ATTR_STYLE_DEFAULT_CLASSES));

                    // Handle style groups
                    List<StyleGroupModel> styleGroups = new ArrayList<>();
                    Element styleGroupsEl = getChild(policyEl, PolicyConstants.STYLE_GROUPS_NODE);
                    if (styleGroupsEl != null) {
                        Node groupNode = styleGroupsEl.getFirstChild();
                        while (groupNode != null) {
                            if (groupNode instanceof Element) {
                                Element groupEl = (Element) groupNode;
                                StyleGroupModel group = new StyleGroupModel();
                                group.setName(groupEl.getAttribute(PolicyConstants.ATTR_STYLE_GROUP_LABEL));
                                group.setAllowCombination(Boolean.parseBoolean(groupEl.getAttribute(PolicyConstants.ATTR_STYLE_GROUP_MULTIPLE)));

                                // Handle individual styles
                                List<StyleModel> styles = new ArrayList<>();
                                Element stylesEl = getChild(groupEl, PolicyConstants.STYLES_NODE);
                                if (stylesEl != null) {
                                    Node styleNode = stylesEl.getFirstChild();
                                    while (styleNode != null) {
                                        if (styleNode instanceof Element) {
                                            Element styleEl = (Element) styleNode;
                                            StyleModel style = new StyleModel();
                                            style.setName(styleEl.getAttribute(PolicyConstants.ATTR_STYLE_LABEL));
                                            style.setCssClass(styleEl.getAttribute(PolicyConstants.ATTR_STYLE_CLASSES));
                                            styles.add(style);
                                        }
                                        styleNode = styleNode.getNextSibling();
                                    }
                                }
                                group.setStyles(styles);
                                styleGroups.add(group);
                            }
                            groupNode = groupNode.getNextSibling();
                        }
                    }

                    model.setStyleGroups(styleGroups);
                    policies.add(model);
                }
                policyNode = policyNode.getNextSibling();
            }

            log.info("{}: Total policies fetched: {}", METHOD, policies.size());

        } catch (Exception e) {
            log.error("{}: Error reading policies for project: {}, component: {}", METHOD, project, componentName, e);
        }

        return policies;
    }


    /**
     * Loads a specific policy by ID for a component in a project.
     * <p>
     * Traverses the central policy XML based on the component's resource type path,
     * and retrieves the requested PolicyModel along with its style groups and styles.
     *
     * @param project   The project name
     * @param component The component name
     * @param policyId  The policy ID to load
     * @return PolicyModel object if found, otherwise null
     */
    @Override
    public PolicyModel loadPolicy(String project, String component, String policyId) {
        final String METHOD = "LOAD_POLICY";
        String path = buildConfPath(project) + PolicyConstants.POLICY_FILE_RELATIVE_PATH;
        File file = new File(path);

        log.info("{}: Loading policy '{}' for project: {}, component: {}", METHOD, policyId, project, component);

        if (!file.exists()) {
            log.warn("{}: Policy file not found at: {}", METHOD, file.getAbsolutePath());
            return null;
        }

        try {
            // Parse XML
            Document doc = newBuilder().parse(file);
            Element root = doc.getDocumentElement();

            // Get component resource type path
            String fullPath = componentService.findComponentPathExact(project, component);
            String resourcePath = PathUtil.getResourceTypeFromPath(fullPath);
            String[] pathParts = resourcePath.split("/");

            // Traverse nodes dynamically based on resourcePath
            Element currentEl = root;
            for (String part : pathParts) {
                if (part.isEmpty()) continue;
                currentEl = getChild(currentEl, part);
                if (currentEl == null) {
                    log.warn("{}: Node not found for path segment: {}", METHOD, part);
                    return null;
                }
            }

            // Locate the specific policy node
            Element policyEl = getChild(currentEl, policyId);
            if (policyEl == null) {
                log.warn("{}: Policy '{}' not found under component '{}'", METHOD, policyId, component);
                return null;
            }

            // Validate resource type
            if (!PolicyConstants.POLICY_RESOURCE_TYPE.equals(policyEl.getAttribute("sling:resourceType"))) {
                log.warn("{}: Invalid resourceType for policy '{}': {}", METHOD, policyId, policyEl.getAttribute("sling:resourceType"));
                return null;
            }

            // Build PolicyModel
            PolicyModel policy = new PolicyModel();
            policy.setId(policyEl.getNodeName());
            policy.setTitle(policyEl.getAttribute(PolicyConstants.ATTR_TITLE));
            policy.setDescription(policyEl.getAttribute(PolicyConstants.ATTR_DESCRIPTION));
            policy.setDefaultCssClass(policyEl.getAttribute(PolicyConstants.ATTR_STYLE_DEFAULT_CLASSES));

            log.info("{}: Loaded policy: id={}, title={}", METHOD, policy.getId(), policy.getTitle());

            // Handle style groups
            List<StyleGroupModel> styleGroups = new ArrayList<>();
            Element styleGroupsEl = getChild(policyEl, PolicyConstants.STYLE_GROUPS_NODE);
            if (styleGroupsEl != null) {
                for (Element groupEl : getChildElements(styleGroupsEl)) {
                    StyleGroupModel group = new StyleGroupModel();
                    group.setName(groupEl.getAttribute(PolicyConstants.ATTR_STYLE_GROUP_LABEL));
                    group.setAllowCombination(Boolean.parseBoolean(groupEl.getAttribute(PolicyConstants.ATTR_STYLE_GROUP_MULTIPLE)));

                    log.info("{}:   Style group: label={}, allowCombination={}", METHOD, group.getName(), group.isAllowCombination());

                    // Handle styles inside group
                    List<StyleModel> styles = new ArrayList<>();
                    Element stylesEl = getChild(groupEl, PolicyConstants.STYLES_NODE);
                    if (stylesEl != null) {
                        for (Element styleEl : getChildElements(stylesEl)) {
                            StyleModel style = new StyleModel();
                            style.setName(styleEl.getAttribute(PolicyConstants.ATTR_STYLE_LABEL));
                            style.setCssClass(styleEl.getAttribute(PolicyConstants.ATTR_STYLE_CLASSES));
                            styles.add(style);

                            log.info("{}:     Style: label={}, cssClass={}", METHOD, style.getName(), style.getCssClass());
                        }
                    }

                    group.setStyles(styles);
                    styleGroups.add(group);
                }
            }

            policy.setStyleGroups(styleGroups);
            return policy;

        } catch (Exception e) {
            log.error("{}: Error loading policy '{}' for component '{}' in project '{}'", METHOD, policyId, component, project, e);
            return null;
        }
    }


    /**
     * Updates the template mapping file with the new policy path.
     * <p>
     * Traverses the mapping XML, creates missing nodes if necessary, and updates
     * the cq:policy attribute for the leaf component. Also updates lastModified metadata.
     *
     * @param mappingFilePath            Path to the template mapping XML file
     * @param componentFullPathWithPolicy Full component path including policy
     */
    private void updateTemplateMapping(String mappingFilePath, String componentFullPathWithPolicy) {
        final String METHOD = "UPDATE_TEMPLATE_MAPPING";

        try {
            File mappingFile = new File(mappingFilePath);
            if (!mappingFile.exists()) {
                throw new IllegalStateException("Mapping file does not exist: " + mappingFilePath);
            }

            // Parse mapping XML
            Document doc = newBuilder().parse(mappingFile);
            Element root = doc.getDocumentElement();

            // Update lastModified metadata on jcr:content
            Element jcrContent = XmlUtil.getOrCreateChild(doc, root, PolicyConstants.JCR_CONTENT_NODE);
            jcrContent.setAttribute(PolicyConstants.ATTR_LAST_MODIFIED, "{Date}" + new Date().toInstant().toString());
            jcrContent.setAttribute(PolicyConstants.ATTR_LAST_MODIFIED_BY, PolicyConstants.DEFAULT_LAST_MODIFIED_BY);

            // Navigate to container node
            Element rootNode = XmlUtil.getOrCreateChild(doc, jcrContent, PolicyConstants.ROOT_NODE);
            Element container =XmlUtil.getOrCreateChild(doc, rootNode, PolicyConstants.CONTAINER_NODE);

            // Split the full path into segments
            String[] segments = componentFullPathWithPolicy.split("/");
            Element current = container;

            // Traverse intermediate nodes (all except the leaf component)
            for (int i = 0; i < segments.length - 2; i++) {
                current = XmlUtil.getOrCreateChild(doc, current, segments[i]);
                current.setAttribute(PolicyConstants.ATTR_PRIMARY_TYPE, PolicyConstants.NT_UNSTRUCTURED);
            }

            // Leaf node = last component before policy ID
            String leafNodeName = segments[segments.length - 2];
            Element leafNode = PolicyUtil.getChildByName(current, leafNodeName);
            if (leafNode == null) {
                leafNode = doc.createElement(leafNodeName);
                current.appendChild(leafNode);
            }

            // Update or set leaf component attributes
            leafNode.setAttribute(PolicyConstants.ATTR_PRIMARY_TYPE, PolicyConstants.NT_UNSTRUCTURED);
            leafNode.setAttribute(PolicyConstants.ATTR_POLICY, componentFullPathWithPolicy);
            leafNode.setAttribute(PolicyConstants.ATTR_RESOURCE_TYPE, PolicyConstants.POLICY_MAPPING_RESOURCE_TYPE);

            // Save XML back to file
            XmlUtil.writeDoc(doc, mappingFile);
            log.info("{}:  Template mapping updated successfully for component: {}", METHOD, leafNodeName);

        } catch (Exception e) {
            log.error("{}: Failed to update template mapping file: {}", METHOD, mappingFilePath, e);
        }
    }

    /**
     * Generates a unique 13-digit ID based on the current timestamp.
     * Ensures uniqueness even if called multiple times in the same millisecond.
     *
     * @return a unique 13-digit long ID
     */
    public static long getUnique13DigitId() {
        final String methodPrefix = "GET_UNIQUE_13_DIGIT_ID: ";

        long now = System.currentTimeMillis();
        long last = lastId.get();

        if (now <= last) {
            // Increment to ensure uniqueness if called in the same millisecond
            now = last + 1;
        }

        lastId.set(now);
        log.info("{}Generated unique ID: {}", methodPrefix, now);
        return now;
    }

}
