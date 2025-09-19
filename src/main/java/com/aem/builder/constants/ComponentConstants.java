package com.aem.builder.constants;

import java.util.Set;

public final class ComponentConstants {
    public static final String CONTENT_XML = ".content.xml";
    public static final String JAVA_SRC_PATH = "core/src/main/java";
    public static final String HTL_FILE_EXTENSION = ".html";
    public static final String CONTENT_ROOT_PATH = "/ui.apps/src/main/content/jcr_root/apps/";
    public static final Set<String> EXCLUDED_FOLDERS = Set.of(
            "_cq_",
            ".",
            "cq:template",
            "new",
            "old",
            "backup"
    );
    public static final String DESIGN_DIALOG = "Design Dialog";
    public static final String AUTHORING_DIALOG_PATH = "cq/gui/components/authoring/dialog";
    public static final String CQ_DIALOG = "_cq_dialog";
    public static final String SLING_RESOURCE_TYPE = "sling:resourceType";
    public static final String SLING_RESOURCE_SUPER_TYPE = "sling:resourceSuperType";
    public static final String SUFFIX_RESOURCE_SUPER_TYPE = ":resourceSuperType";
    public static final String JCR_TITLE = "jcr:title";
    public static final String GRANITE_MULTIFIELD = "granite/ui/components/coral/foundation/form/multifield";
    public static final String GRANITE_SELECT = "granite/ui/components/coral/foundation/form/select";
    public static final String GRANITE_TABS = "granite/ui/components/coral/foundation/tabs";
    public static final String GRANITE_CONTAINER = "granite/ui/components/coral/foundation/container";
    public static final String CQ_FILEUPLOAD = "cq/gui/components/authoring/dialog/fileupload";
    public static final String IMAGE = "image";
    public static final String ITEMS = "items";
    public static final String HIDDEN_GROUP = ".hidden";
    public static final String STRUCTURE_GROUP_SUFFIX = " - Structure";
    public static final String FORM_GROUP_SUFFIX = " - Form";
    public static final String DEFAULT_COMPONENT_GROUP = "Others";
    public static final String CHILD_CLASS_FIELD_PATTERN =
            "(?:public|protected|public)?\\s*(?:List<([A-Z]\\w+)>|Set<([A-Z]\\w+)>|([A-Z]\\w+))\\s+\\w+;";
    public static final String DATA_SLY_USE_PATTERN = "data-sly-use(?:\\.[A-Za-z0-9_-]+)?\\s*=\\s*\"([^\"]+)\"";
    public static final String DATA_SLY_USE = "data-sly-use";
    public static final String COMPONENTS_CLASSPATH_PATTERN = "classpath:/aem-components/*";
    public static final String CQ_COMPONENT_PRIMARY_TYPE = "cq:Component";
    public static final String UNIQUE_KEY = "unique";
    public static final String DUPLICATE_KEY = "duplicate";
    public static final String MODEL_SUFFIX = "Model";
    public static final String JAVA_EXTENSION = ".java";
    public static final String IMPORTS_BLOCK =
            "import java.util.List;\n" +
                    "import javax.inject.Inject;\n" +
                    "import org.apache.sling.api.resource.Resource;\n" +
                    "import org.apache.sling.models.annotations.DefaultInjectionStrategy;\n" +
                    "import org.apache.sling.models.annotations.Model;\n" +
                    "import org.apache.sling.models.annotations.injectorspecific.ValueMapValue;\n" +
                    "import org.apache.sling.models.annotations.injectorspecific.ChildResource;\n\n";
    public static final String MODEL_ANNOTATION =
            "@Model(adaptables = Resource.class, defaultInjectionStrategy = DefaultInjectionStrategy.OPTIONAL)\n";
    public static final String MODEL_GEN_PREFIX = "MODEL_GEN:";
    public static final String TYPE_MULTIFIELD = "multifield";
    public static final String TYPE_CHECKBOX = "checkbox";
    public static final String TYPE_MULTISELECT = "multiselect";
    public static final String TYPE_TAGFIELD = "tagfield";
    public static final String TYPE_NUMBERFIELD = "numberfield";
    public static final String TYPE_TABS = "tabs";
    public static final String SELECT = "select";
    public static final String RADIOGROUP = "radiogroup";
    public static final String FILEUPLOAD = "fileupload";
    public static final String CHILD_RESOURCE_ANNOTATION = "@ChildResource";
    public static final String VALUE_MAP_ANNOTATION = "@ValueMapValue";
    public static final String ATTR_FILE_REFERENCE = "fileReferenceParameter";
    public static final String ATTR_FIELD = "field";
    public static final String ATTR_TEXT = "text";
    public static final String ATTR_VALUE = "value";
    public static final String ATTR_MULTIPLE = "multiple";
    public static final String VALUE_TRUE = "true";
    public static final String MODELS_FOLDER = "models";
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
    public static final String KEY_NAME = "name";
    public static final String KEY_PATH = "path";
    public static final String SUPER_TYPE = "superType";
    public static final String COMPONENTS_FOLDER = "components";
    public static final String DATA_SLY_USE_MODEL_PATTERN = "data-sly-use\\.model=\"[^\"]+\"";
    public static final String SLING_RESOURCE_TYPE_PATTERN = "sling:resourceType=\"[^\"]+\"";
    public static final String CLASS_DECLARATION_REGEX = "public\\s+class\\s+(\\w+)";
    public static final String COMPONENT_GROUP = "componentGroup";
    public static final String SLING_RESOURCE_TYPE_ATTR = "sling:resourceType=\"";
    public static final String DATA_SLY_USE_MODEL = "data-sly-use.model=\"";
    public static final String POM_FILE_NAME = "pom.xml";
    public static final String JCR_PRIMARY_TYPE = "jcr:primaryType";
    public static final String PROPERTY_REGEX = "=\"(?:\\{String\\})?([^\"]+)\"";
    public static final String JAVA_CLASS_REF_PATTERN = "\\b([A-Z][A-Za-z0-9_]+)\\b";
    public static final String SLING_MODELS_SOURCE = "src/main/java/com/aem/builder/slingModels";
    public static final String AEM_COMPONENTS_SOURCE = "src/main/resources/aem-components";
    public static final String UI_APPS_JCR_ROOT = "/ui.apps/src/main/content/jcr_root";
    public static final String ATTR_FIELD_LABEL = "fieldLabel";

}