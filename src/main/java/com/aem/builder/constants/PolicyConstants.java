package com.aem.builder.constants;

/**
 * Constants used for AEM policy management.
 * Includes XML nodes, JCR attributes, resource types, default values, and file paths.
 */
public final class PolicyConstants {

    private PolicyConstants() {}

    // ----------------- File paths / relative paths -----------------
    public static final String CONF_RELATIVE_PATH = "/ui.content/src/main/content/jcr_root/conf/";
    public static final String WCM_TEMPLATES_RELATIVE_PATH = "/settings/wcm/templates/";
    public static final String STRUCTURE_FILE = "/structure/.content.xml";
    public static final String POLICY_FILE = "/policies/.content.xml";
    public static final String SETTINGS_WCM_POLICY_PATH = "/settings/wcm" + POLICY_FILE;
    public static final String COMPONENTS_PATH_RELATIVE = "/ui.apps/src/main/content/jcr_root/apps/";
    public static final String DESIGN_DIALOG_PATH = "/_cq_design_dialog/.content.xml";
    public static final String POLICIES_RELATIVE_PATH = "/settings/wcm/policies";
    public static final String POLICY_FILE_RELATIVE_PATH = "/settings/wcm/policies/.content.xml";

    // ----------------- Resource types -----------------
    public static final String POLICY_RESOURCE_TYPE = "wcm/core/components/policy/policy";
    public static final String POLICY_MAPPING_RESOURCE_TYPE = "wcm/core/components/policies/mapping";

    // ----------------- XML nodes -----------------
    public static final String CONTENT_NODE = "jcr:content";
    public static final String JCR_CONTENT_NODE = "jcr:content"; // for template mapping
    public static final String STYLE_GROUPS_NODE = "cq:styleGroups";
    public static final String STYLES_NODE = "cq:styles";
    public static final String ROOT_NODE = "root";           // used in template mapping
    public static final String CONTAINER_NODE = "container"; // used in template mapping

    // ----------------- JCR attributes -----------------
    public static final String ATTR_PRIMARY_TYPE = "jcr:primaryType";
    public static final String ATTR_TITLE = "jcr:title";
    public static final String ATTR_DESCRIPTION = "jcr:description";
    public static final String ATTR_STYLE_DEFAULT_CLASSES = "cq:styleDefaultClasses";
    public static final String ATTR_LAST_MODIFIED_BY = "cq:lastModifiedBy";
    public static final String ATTR_LAST_MODIFIED = "cq:lastModified";
    public static final String ATTR_POLICY = "cq:policy";
    public static final String ATTR_RESOURCE_TYPE = "sling:resourceType";

    // ----------------- Style group attributes -----------------
    public static final String ATTR_STYLE_GROUP_LABEL = "cq:styleGroupLabel";
    public static final String ATTR_STYLE_GROUP_MULTIPLE = "cq:styleGroupMultiple";

    // ----------------- Style attributes -----------------
    public static final String ATTR_STYLE_LABEL = "cq:styleLabel";
    public static final String ATTR_STYLE_CLASSES = "cq:styleClasses";
    public static final String ATTR_STYLE_ID = "cq:styleId";

    // ----------------- Defaults -----------------
    public static final String NT_UNSTRUCTURED = "nt:unstructured";
    public static final String DEFAULT_LAST_MODIFIED_BY = "admin";

}
