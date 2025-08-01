

package com.aem.builder.util;

public class TemplateUtil {
    public static String generatePoliciesXmlXf(String projectname){
        return """
            <?xml version="1.0" encoding="UTF-8"?>
            <jcr:root xmlns:sling="http://sling.apache.org/jcr/sling/1.0" xmlns:cq="http://www.day.com/jcr/cq/1.0" xmlns:jcr="http://www.jcp.org/jcr/1.0" xmlns:nt="http://www.jcp.org/jcr/nt/1.0"
                jcr:primaryType="cq:Page">
                <jcr:content
                    jcr:primaryType="nt:unstructured"
                    sling:resourceType="wcm/core/components/policies/mappings">
                    <root
                        cq:policy="%s/components/container/policy_1575040440977"
                        jcr:primaryType="nt:unstructured"
                        sling:resourceType="wcm/core/components/policies/mapping"/>
                </jcr:content>
            </jcr:root>
            
            """.formatted(projectname);
    }

    public static String generateStructureContentXmlXf(String projectname,String templatename){
        return """
            <?xml version="1.0" encoding="UTF-8"?>
            <jcr:root xmlns:sling="http://sling.apache.org/jcr/sling/1.0" xmlns:cq="http://www.day.com/jcr/cq/1.0" xmlns:jcr="http://www.jcp.org/jcr/1.0" xmlns:nt="http://www.jcp.org/jcr/nt/1.0"
                jcr:primaryType="cq:Page">
                <jcr:content
                    cq:deviceGroups="[mobile/groups/responsive]"
                    cq:template="/conf/%s/settings/wcm/templates/%s"
                    jcr:primaryType="cq:PageContent"
                    sling:resourceType="%s/components/xfpage">
                    <root
                        jcr:primaryType="nt:unstructured"
                        sling:resourceType="%s/components/container"
                        editable="{Boolean}true"
                        layout="responsiveGrid"/>
                    <cq:responsive jcr:primaryType="nt:unstructured">
                        <breakpoints jcr:primaryType="nt:unstructured">
                            <phone
                                jcr:primaryType="nt:unstructured"
                                title="Smaller Screen"
                                width="{Long}768"/>
                            <tablet
                                jcr:primaryType="nt:unstructured"
                                title="Tablet"
                                width="{Long}1200"/>
                        </breakpoints>
                    </cq:responsive>
                </jcr:content>
            </jcr:root>
            """.formatted(projectname,templatename,projectname,projectname);
    }


    //page template
    public static String getJcrContentXmlPage(String templatename, String projectName,String status,String templatetype) { // Add projectName parameter
        return """
        <?xml version="1.0" encoding="UTF-8"?>
        <jcr:root
            xmlns:sling="http://sling.apache.org/jcr/sling/1.0"
            xmlns:jcr="http://www.jcp.org/jcr/1.0"
            xmlns:cq="http://www.day.com/jcr/cq/1.0"
            jcr:primaryType="cq:PageContent"
            jcr:title="%s"
            cq:templateType="/conf/%s/settings/wcm/template-types/%s"
            status="%s"/>
           \s
           \s""".formatted( templatename,
                projectName,templatetype,status);
    }
    //node properties

    public static String getTemplateRootXmlPage(String templatename) {
        return """
        <?xml version="1.0" encoding="UTF-8"?>
        <jcr:root
            xmlns:sling="http://sling.apache.org/jcr/sling/1.0"
            xmlns:jcr="http://www.jcp.org/jcr/1.0"
            jcr:primaryType="cq:Template"
            jcr:title="%s"/>
        """.formatted(templatename);
    }

    //initial


    public static String getInitialXmlPage(String projectname,String templatename) {
        return """
        <?xml version="1.0" encoding="UTF-8"?>
                <jcr:root xmlns:sling="http://sling.apache.org/jcr/sling/1.0" xmlns:cq="http://www.day.com/jcr/cq/1.0" xmlns:jcr="http://www.jcp.org/jcr/1.0" xmlns:nt="http://www.jcp.org/jcr/nt/1.0"
                    jcr:primaryType="cq:Page">
                    <jcr:content
                        cq:template="/conf/%s/settings/wcm/templates/%s"
                        jcr:primaryType="cq:PageContent"
                        sling:resourceType="%s/components/page">
                        <root
                            jcr:primaryType="nt:unstructured"
                            sling:resourceType="%s/components/container"
                            layout="responsiveGrid"/>
                           \s
                    </jcr:content>
                </jcr:root>
               \s""".formatted(projectname,templatename,projectname,projectname,projectname);
    }

    //structure


    public static String getStructureXmlPage(String templatename,String projectnane) { // Add projectName parameter
        return """
        <?xml version="1.0" encoding="UTF-8"?>
        <jcr:root
            xmlns:sling="http://sling.apache.org/jcr/sling/1.0"
            xmlns:cq="http://www.day.com/jcr/cq/1.0"
            xmlns:jcr="http://www.jcp.org/jcr/1.0"
            xmlns:nt="http://www.jcp.org/jcr/nt/1.0"
            jcr:primaryType="cq:Page">
            <jcr:content
                cq:deviceGroups="[mobile/groups/responsive]"
                cq:lastModified="{Date}2025-07-21T10:21:18.990+05:30"
                cq:lastModifiedBy="admin"
                cq:template="/conf/%s/settings/wcm/templates/%s"
                jcr:primaryType="cq:PageContent"
                sling:resourceType="%s/components/page">
                <root
                    jcr:primaryType="nt:unstructured"
                    sling:resourceType="%s/components/container"
                    layout="responsiveGrid">
                    <experiencefragment-header
                                            jcr:primaryType="nt:unstructured"
                                            sling:resourceType="%s/components/experiencefragment"
                                            fragmentVariationPath="/content/experience-fragments/%s/language-masters/en/site/header/master"/>
                                     \s
                    <container
                        jcr:primaryType="nt:unstructured"
                        sling:resourceType="%s/components/container"
                        editable="{Boolean}true"
                        layout="responsiveGrid"/>
                </root>
                <cq:responsive jcr:primaryType="nt:unstructured">
                    <breakpoints jcr:primaryType="nt:unstructured">
                        <phone
                            jcr:primaryType="nt:unstructured"
                            title="Smaller Screen"
                            width="{Long}768"/>
                        <tablet
                            jcr:primaryType="nt:unstructured"
                            title="Tablet"
                            width="{Long}1200"/>
                    </breakpoints>
                </cq:responsive>
            </jcr:content>
        </jcr:root>
        """.formatted(projectnane,templatename,projectnane,projectnane,projectnane,projectnane,projectnane); // Use projectName for resource types
    }


    public static String getPoliciesPage(String projectname) {
        return """
        <?xml version="1.0" encoding="UTF-8"?>
        <jcr:root xmlns:sling="http://sling.apache.org/jcr/sling/1.0" xmlns:cq="http://www.day.com/jcr/cq/1.0" xmlns:jcr="http://www.jcp.org/jcr/1.0" xmlns:nt="http://www.jcp.org/jcr/nt/1.0"
        jcr:primaryType="cq:Page">
                <jcr:content
        cq:policy="%s/components/page/policy"
        jcr:primaryType="nt:unstructured"
        sling:resourceType="wcm/core/components/policies/mappings">
                <root
        cq:policy="%s/components/container/policy_1574694950110"
        jcr:primaryType="nt:unstructured"
        sling:resourceType="wcm/core/components/policies/mapping">
                <container
        cq:policy="%s/components/container/policy_1574695586800"
        jcr:primaryType="nt:unstructured"
        sling:resourceType="wcm/core/components/policies/mapping"/>
                </root>
                </jcr:content>
                </jcr:root>
               \s""".formatted(projectname,projectname,projectname);
    }

}