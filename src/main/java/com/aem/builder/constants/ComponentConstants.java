package com.aem.builder.constants;

import java.util.Set;

/**
 * Centralized constants used for handling AEM components, dialogs, and related configurations.
 * <p>
 * This class helps avoid hardcoding repeated strings (like file names, resource types, or XML node names)
 * by consolidating them in one place. It improves readability, maintainability, and reduces errors.
 * </p>
 *
 * @author
 * @since 1.0
 */
public final class ComponentConstants {

    /**
     * Name of the AEM component descriptor file.
     */
    public static final String CONTENT_XML = ".content.xml";

    /**
     * Path to Java source folder inside a project.
     */
    public static final String JAVA_SRC_PATH = "core/src/main/java";

    /**
     * File extension for HTL scripts.
     */
    public static final String HTL_FILE_EXTENSION = ".html";

    /**
     * Root path for component content inside AEM apps folder.
     */
    public static final String CONTENT_ROOT_PATH = "/ui.apps/src/main/content/jcr_root/apps/";


    /**
     * Folders that should be excluded while scanning components.
     */
    public static final Set<String> EXCLUDED_FOLDERS = Set.of(
            "_cq_",     // prefixes like _cq_design_dialog, _cq_template
            ".",        // hidden folders
            "cq:template",
            "new",
            "old",
            "backup"
    );

    /**
     * Label for design dialogs.
     */
    public static final String DESIGN_DIALOG = "Design Dialog";

    /**
     * Path to authoring dialogs in AEM.
     */
    public static final String AUTHORING_DIALOG_PATH = "cq/gui/components/authoring/dialog";

    /**
     * Folder containing the dialog definition of a component.
     */
    public static final String CQ_DIALOG = "_cq_dialog";

    /**
     * Regex pattern to match tab versioning (e.g., v1, v2).
     */
    public static final String TAB_VERSION_REGEX = "v\\d+";


    /**
     * Sling resource type attribute key.
     */
    public static final String SLING_RESOURCE_TYPE = "sling:resourceType";


    /**
     * Sling resource super type attribute key.
     */
    public static final String SLING_RESOURCE_SUPER_TYPE = "sling:resourceSuperType";

    /**
     * Alternative suffix for namespaced resourceSuperType.
     */
    public static final String SUFFIX_RESOURCE_SUPER_TYPE = ":resourceSuperType";

    /**
     * Attribute key for tab titles.
     */
    public static final String JCR_TITLE = "jcr:title";


    /**
     * Granite UI Multifield resource type.
     */
    public static final String GRANITE_MULTIFIELD = "granite/ui/components/coral/foundation/form/multifield";

    /**
     * Granite UI Select resource type.
     */
    public static final String GRANITE_SELECT = "granite/ui/components/coral/foundation/form/select";

    /**
     * Granite UI Tabs resource type.
     */
    public static final String GRANITE_TABS = "granite/ui/components/coral/foundation/tabs";

    /**
     * Granite UI Container resource type.
     */
    public static final String GRANITE_CONTAINER = "granite/ui/components/coral/foundation/container";

    /**
     * File upload resource type in CQ dialogs.
     */
    public static final String CQ_FILEUPLOAD = "cq/gui/components/authoring/dialog/fileupload";

    public static final String IMAGE = "image";
    public static final String ITEMS = "items";


    /**
     * Hidden group (not shown in dialog).
     */
    public static final String HIDDEN_GROUP = ".hidden";

    /**
     * Suffix used for structure groups.
     */
    public static final String STRUCTURE_GROUP_SUFFIX = " - Structure";

    /**
     * Suffix used for form groups.
     */
    public static final String FORM_GROUP_SUFFIX = " - Form";

    /**
     * Default component group name.
     */
    public static final String DEFAULT_COMPONENT_GROUP = "Others";

    /**
     * Group name placeholder used in POM.
     */
    public static final String POM_COMPONENT_GROUP_TAG = "componentGroupName";


    /**
     * Java package prefix for project source code.
     */
    public static final String JAVA_PACKAGE_PREFIX = "com.";

    /**
     * Regex for detecting child class fields in Java.
     */
    public static final String CHILD_CLASS_FIELD_PATTERN =
            "(?:public|protected|public)?\\s*(?:List<([A-Z]\\w+)>|Set<([A-Z]\\w+)>|([A-Z]\\w+))\\s+\\w+;";

    /**
     * Regex for extracting data-sly-use values.
     */
    public static final String DATA_SLY_USE_PATTERN = "data-sly-use(?:\\.[A-Za-z0-9_-]+)?\\s*=\\s*\"([^\"]+)\"";


    /**
     * data-sly-use attribute in HTL files.
     */
    public static final String DATA_SLY_USE = "data-sly-use";

    /**
     * Classpath scanning pattern for loading AEM component definitions.
     * <p>
     * Used when resolving resources from the {@code aem-components} directory
     * on the classpath, typically in {@code src/main/resources}.
     * </p>
     */
    public static final String COMPONENTS_CLASSPATH_PATTERN = "classpath:/aem-components/*";

    /**
     * JCR primary type value that identifies an AEM component node.
     * <p>
     * Used in {@code .content.xml} files to declare the node as
     * a {@code cq:Component}.
     * </p>
     */
    public static final String CQ_COMPONENT_PRIMARY_TYPE = "cq:Component";

    // Response map keys
    public static final String UNIQUE_KEY = "unique";
    public static final String DUPLICATE_KEY = "duplicate";

    // File & naming
    public static final String MODEL_SUFFIX = "Model";
    public static final String JAVA_EXTENSION = ".java";

    // Package & imports
    public static final String IMPORTS_BLOCK =
            "import java.util.List;\n" +
                    "import javax.inject.Inject;\n" +
                    "import org.apache.sling.api.resource.Resource;\n" +
                    "import org.apache.sling.models.annotations.DefaultInjectionStrategy;\n" +
                    "import org.apache.sling.models.annotations.Model;\n" +
                    "import org.apache.sling.models.annotations.injectorspecific.ValueMapValue;\n" +
                    "import org.apache.sling.models.annotations.injectorspecific.ChildResource;\n\n";

    // Annotation template
    public static final String MODEL_ANNOTATION =
            "@Model(adaptables = Resource.class, defaultInjectionStrategy = DefaultInjectionStrategy.OPTIONAL)\n";

    // Log prefixes
    public static final String MODEL_GEN_PREFIX = "MODEL_GEN:";

    // Field types
    public static final String TYPE_MULTIFIELD = "multifield";
    public static final String TYPE_CHECKBOX = "checkbox";
    public static final String TYPE_MULTISELECT = "multiselect";
    public static final String TYPE_TAGFIELD = "tagfield";
    public static final String TYPE_NUMBERFIELD = "numberfield";
    public static final String TYPE_TABS = "tabs";

    public static final String SELECT = "select";
    public static final String RADIOGROUP = "radiogroup";
    public static final String FILEUPLOAD = "fileupload";
    // Annotations
    public static final String CHILD_RESOURCE_ANNOTATION = "@ChildResource";
    public static final String VALUE_MAP_ANNOTATION = "@ValueMapValue";
    public static final String ATTR_FILE_REFERENCE = "fileReferenceParameter";
    public static final String ATTR_FIELD = "field";
    public static final String ATTR_TEXT = "text";
    public static final String ATTR_VALUE = "value";
    public static final String ATTR_MULTIPLE = "multiple";
    public static final String VALUE_TRUE = "true";

    // Default primitive values (optional, can also be in JSON)
    public static final String EMPTY_STRING = "";
    public static final double DEFAULT_NUMBER = 0;


    /**
     * Java file extension string.
     */
    public static final String MODELS_FOLDER = "models";


    /**
     * Prefix for application components path in AEM.
     */
    public static final String APPS_PATH_PREFIX = "/apps/";

    public static final String WELL = "granite/ui/components/coral/foundation/well";
    public static final String TEXT = "granite/ui/components/coral/foundation/text";
    public static final String INCLUDE = "granite/ui/components/coral/foundation/include";
    public static final String COMPONENTS_PATH = "ui.apps/src/main/content/jcr_root/apps";
    public static final String DIALOG_FILE = "_cq_dialog/.content.xml";
    public static final String CORE_PREFIX = "core/";
    public static final String MSM_FOLDER = "msm";

    public static final String MESSAGE = "message";
    public static final String ERROR = "error";
    public static final String LOG_PREFIX = "[ComponentController]";
    public static final String KEY_NAME = "name";
    public static final String KEY_PATH = "path";
    public static final String SUPER_TYPE = "superType";

    // Paths
    public static final String SLING_MODELS_DIR = "com/aem/builder/slingModels";
    public static final String MODELS_BASE_DIR = "models";
    public static final String COMPONENTS_FOLDER = "components";

    // Regex patterns
    public static final String DATA_SLY_USE_MODEL_PATTERN = "data-sly-use\\.model=\"[^\"]+\"";
    public static final String SLING_RESOURCE_TYPE_PATTERN = "sling:resourceType=\"[^\"]+\"";
    public static final String CLASS_DECLARATION_REGEX = "public\\s+class\\s+(\\w+)";
    public static final String CLASS_USAGE_PATTERN = "\\b%s\\b";
    public static final String COMPONENT_GROUP_PROP = "componentGroup";
    public static final String SLING_RESOURCE_TYPE_ATTR = "sling:resourceType=\"";
    public static final String DATA_SLY_USE_MODEL = "data-sly-use.model=\"";

    public static final String POM_FILE_NAME = "pom.xml";
    // Optional for clarity:
    public static final boolean COMPONENT_AVAILABLE = true;
    public static final boolean COMPONENT_EXISTS = false;
    // public static final String COMPONENTS_FOLDER_NAME = "components";
    public static final String AEM_COMPONENTS_BASE_PATH = "/apps/%s/components";
    public static final String JCR_PRIMARY_TYPE = "jcr:primaryType";
    public static final String COMPONENT_GROUP_ATTR = "componentGroup";
    // Key used in map to indicate if a component has tabs
    public static final String HAS_TABS_KEY = "hasTabs";

    public static final String PROPERTY_REGEX = "%s=\"(?:\\{String\\})?([^\"]+)\"";

    /**
     * Regex pattern to match Java class names in source code.
     * Matches words starting with a capital letter followed by letters, digits, or underscores.
     */
    public static final String JAVA_CLASS_REF_PATTERN = "\\b([A-Z][A-Za-z0-9_]+)\\b";
    public static final String SLING_MODELS_SOURCE = "src/main/java/com/aem/builder/slingModels";
    public static final String AEM_COMPONENTS_SOURCE = "src/main/resources/aem-components";
    public static final String UI_APPS_JCR_ROOT = "/ui.apps/src/main/content/jcr_root";
    public static final String ATTR_FIELD_LABEL = "fieldLabel";

}
