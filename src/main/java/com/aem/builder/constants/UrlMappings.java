package com.aem.builder.constants;

public class UrlMappings {
    public static final String FETCH_TEMPLATES = "/fetch-templates/{projectname}";
    public static final String ADD_TEMPLATE = "/add-template/{projectname}";
    public static final String CREATE_TEMPLATE = "/create-template/{projectname}";
    public static final String CREATE_TEMPLATE_GET = "/{projectName}/createtemplate";
    public static final String LIST_TEMPLATES = "/templates/list/{projectname}";
    public static final String TEMPLATE_TYPES = "/template-types/{projectName}";
    public static final String EDIT_TEMPLATE = "/{projectName}/edittemplate";
    public static final String UPDATE_TEMPLATE = "/{projectname}/updatetemplate/{templateName}";

    public static final String ADD_OR_UPDATE_POLICY = "/policies/add/{projectName}";
    public static final String REDIRECT_POLICY_FORM = "/{projectName}/addpolicy";
    public static final String GET_EXISTING_POLICIES = "/get-existing-policies";
    public static final String GET_POLICY_DETAILS = "/get-policy-details";

    public static final String LOAD_COMPONENT_POLICY = "/api/{project}/component/policy";
    public static final String SHOW_TEMPLATE_COMPONENTS = "/{project}/templates/{template}/components";
    public static final String SHOW_POLICY_EDITOR = "/{project}/templates/{template}/component";
    public static final String SAVE_COMPONENT_POLICY = "/api/{project}/templates/{template}/component/policy";

}
