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
public static final String XML_ROOT="jcr:root";


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



    public static final String UI_CONTENT_PATH = "/ui.content/src/main/content/jcr_root/conf/";
    public static final String WCM_TEMPLATES_PATH = "/settings/wcm/templates";
    public static final String RESOURCE_TEMPLATES_DIR = "src/main/resources/aem-templates/";
    public static final String WCM_TEMPLATE_TYPES_PATH = "/settings/wcm/template-types/";
    ///settings/wcm/template-types/
    public static final String INITIAL = "/initial";
    public static final String STRUCTURE = "/structure";
    public static final String POLICIES = "/policies";
    public static final String CONTENT_FILE = "/.content.xml";
    public static final String COMPONENT_XFPAGE_PATH = "/components/xfpage";
    public static final String JCR_CONTENT_TAG = "jcr:content";
    public static final String ATTR_TEMPLATE_TYPE = "cq:templateType";
    public static final String ATTR_TEMPLATE="cq:template";
    public static final String CONF_PATH = "/conf/";
    public static final String POLICIES_BASE_PATH = "generated-projects/%s/ui.content/src/main/content/jcr_root/conf/%s/settings/wcm/policies/.content.xml";
    public static final String GENERATED_PROJECTS_PATH = "generated-projects/";
    public static final String TEMPLATES_SUBPATH = "/settings/wcm/templates/";
    public static final String POLICIES_SUBPATH = "/policies/.content.xml";
    public static final String POLICIES_PATH = "/settings/wcm/policies/.content.xml";

    public static final String CONTAINER_PATH="/components/container";
    // XML attribute constants

    public static final String TAG_STYLE_GROUPS = "cq:styleGroups";
    public static final String TAG_STYLE_GROUP = "item";
    public static final String TAG_CQ_STYLES = "cq:styles";
    public static final String TAG_STYLE_ITEM = "item";
    public static final String ATTR_JCR_TITLE = "jcr:title";
    public static final String ATTR_SLING_RESOURCE_TYPE = "sling:resourceType";
    public static final String ATTR_COMPONENTS = "components";
    public static final String ATTR_LAYOUT_DISABLED = "layoutDisabled";
    public static final String ATTR_JCR_LAST_MODIFIED = "jcr:lastModified";
    public static final String ATTR_JCR_LAST_MODIFIED_BY = "jcr:lastModifiedBy";
    public static final String ATTR_CQ_STYLE_DEFAULT_CLASSES = "cq:styleDefaultClasses";
    public static final String ATTR_CQ_STYLE_DEFAULT_ELEMENT = "cq:styleDefaultElement";
    public static final String ATTR_JCR_PRIMARY_TYPE = "jcr:primaryType";
    public static final String ATTR_STYLE_ELEMENT = "cq:styleElement";
    public static final String ATTR_STYLE_DEFAULT_ELEMENT = "cq:styleDefaultElement";
    public static final String STATUS="status";
    public static final String ATT_DESCRIPTION="jcr:description";


    public static final String ATT_XMLNS_SLING="xmlns:sling";
    public static final String ATT_XMLNS_CQ="xmlns:cq";
    public static final String ATT_XMLNS_JCR="xmlns:jcr";
    public static final String ATT_XMLNS_NT="xmlns:nt";
    public static final String ATT_XMLNS_SLING_VALUE="http://sling.apache.org/jcr/sling/1.0";
    public static final String ATT_XMLNS_CQ_VALUE="http://www.day.com/jcr/cq/1.0";
    public static final String ATT_XMLNS_JCR_VALUE="http://www.jcp.org/jcr/1.0";
    public static final String ATT_XMLNS_NT_VALUE="http://www.jcp.org/jcr/nt/1.0";
    public static final String EXPERIENCE_HEADER="experiencefragment-header";
    public static final String EXPERIENCEFRAGMENT_PATH = "/components/experiencefragment";
    public static final String XF_HEADER_PATH = "/content/experience-fragments/%s/language-masters/en/site/header/master";
    public static final String EDITABLE="editable";
    public static final String COMPONENT_PAGE="/components/page";
    public static final String LANGUAGE_MASTER="/language-masters/en/site/header/master";
    public static final String EXP_FRAGMENT="/content/experience-fragments/";
    public static final String EDITABLE_VALUE="{Boolean}true";
    public static final String LAYOUT="layout";
    public static final String LAYOUT_VALUE="responsiveGrid";
    public static final String FRAG_VAR="fragmentVariationPath";
    public static final String CONTENT_XML=".content.xml";


}
