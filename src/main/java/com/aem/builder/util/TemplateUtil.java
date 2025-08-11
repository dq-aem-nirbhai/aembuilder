

package com.aem.builder.util;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

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
            status="%s">
            </jcr:root>
           \s
           \s""".formatted( templatename,
                projectName,templatetype,status);
    }
    /*
    <?xml version="1.0" encoding="UTF-8"?>
<jcr:root xmlns:cq="http://www.day.com/jcr/cq/1.0" xmlns:jcr="http://www.jcp.org/jcr/1.0"
    jcr:primaryType="cq:Template">
    <jcr:content
    cq:lastModified="{Date}2025-07-21T10:24:05.211+05:30"
    cq:lastModifiedBy="admin"
    cq:templateType="/conf/myFirstAemProject/settings/wcm/template-types/page"
    jcr:primaryType="cq:PageContent"
    jcr:title="template2"
    status="enabled"/>
</jcr:root>

     */
    //node properties

    public static String getTemplateRootXmlPage(String templateName, String projectName, String templateType, String status, String description) {
        return """
        <?xml version="1.0" encoding="UTF-8"?>
        <jcr:root
            xmlns:sling="http://sling.apache.org/jcr/sling/1.0"
            xmlns:jcr="http://www.jcp.org/jcr/1.0"
            xmlns:cq="http://www.day.com/jcr/cq/1.0"
            jcr:primaryType="cq:Template"
            jcr:title="%s">
            <jcr:content
                cq:templateType="/conf/%s/settings/wcm/template-types/%s"
                jcr:description="%s"
                jcr:primaryType="cq:PageContent"
                jcr:title="%s"
                status="%s"/>
        </jcr:root>
        """.formatted(templateName, projectName, templateType, description, templateName, status);
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

    public static void copyTemplate(String fromType,String projectname,String templatename ) throws IOException {
        String basePath = "generated-projects/"+projectname+"/ui.content/src/main/content/jcr_root/conf/"+projectname+"/settings/wcm/template-types/";

        String url = "generated-projects/" + projectname + "/ui.content/src/main/content/jcr_root/conf/"
                + projectname + "/settings/wcm/templates/" +templatename;
        String targetpath = url;

        // Create parent directory
        new File(url).mkdirs();

        Path source = Path.of(basePath + fromType + "/initial/.content.xml");
        Path targetDir = Path.of(targetpath + "/initial/");
        Path target = targetDir.resolve(".content.xml");



        // Create target directories if they don't exist
        Files.createDirectories(targetDir);

        // Copy file
        Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);

        //policies
        Path source1 = Path.of(basePath + fromType + "/policies/.content.xml");
        Path targetDir1 = Path.of(targetpath + "/policies/");
        Path target1= targetDir1.resolve(".content.xml");
        Files.createDirectories(targetDir1);
        Files.copy(source1, target1, StandardCopyOption.REPLACE_EXISTING);

        //structure
        Path source2 = Path.of(basePath + fromType + "/structure/.content.xml");
        Path targetDir2 = Path.of(targetpath + "/structure/");
        Path target2= targetDir2.resolve(".content.xml");

        Files.createDirectories(targetDir2);


        Files.copy(source2, target2, StandardCopyOption.REPLACE_EXISTING);

//node properties
        Path source3 = Path.of(basePath + fromType + "/.content.xml");
        Path targetDir3 = Path.of(targetpath );
        Path target3= targetDir3.resolve(".content.xml");

        Files.createDirectories(targetDir3);


        Files.copy(source3, target3, StandardCopyOption.REPLACE_EXISTING);
    }
public static String getIntialContentXf(String projectname,String templatename){
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <jcr:root xmlns:sling="http://sling.apache.org/jcr/sling/1.0" xmlns:cq="http://www.day.com/jcr/cq/1.0" xmlns:jcr="http://www.jcp.org/jcr/1.0" xmlns:nt="http://www.jcp.org/jcr/nt/1.0"
                    jcr:primaryType="cq:Page">
                    <jcr:content
                        cq:tags="[experience-fragments:variation/web]"
                        cq:template="/conf/%s/settings/wcm/templates/%s"
                        cq:xfVariantType="web"
                        jcr:primaryType="cq:PageContent"
                        sling:resourceType="%s/components/xfpage">
                        <root
                            jcr:primaryType="nt:unstructured"
                            sling:resourceType="%s/components/container"
                            layout="responsiveGrid"/>
                    </jcr:content>
                </jcr:root>
                
                """.formatted(projectname,templatename,projectname,projectname);
}
public static String policyForParticularTemplate(String policynode,String projectname){
    System.out.println("policy   "+policynode);
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <jcr:root xmlns:sling="http://sling.apache.org/jcr/sling/1.0" xmlns:cq="http://www.day.com/jcr/cq/1.0" xmlns:jcr="http://www.jcp.org/jcr/1.0" xmlns:nt="http://www.jcp.org/jcr/nt/1.0"
                    jcr:primaryType="cq:Page">
                    <jcr:content
                        cq:lastModified="{Date}2025-08-06T11:26:05.255+05:30"
                        cq:lastModifiedBy="admin"
                        cq:policy="%s/components/page/policy"
                        jcr:primaryType="nt:unstructured"
                        sling:resourceType="wcm/core/components/policies/mappings">
                        <root
                            cq:policy="%s/components/container/policy_1574694950110"
                            jcr:primaryType="nt:unstructured"
                            sling:resourceType="wcm/core/components/policies/mapping">
                            <container
                                cq:policy="%s/components/container/%s"
                                jcr:primaryType="nt:unstructured"
                                sling:resourceType="wcm/core/components/policies/mapping"/>
                        </root>
                    </jcr:content>
                </jcr:root>
                """.formatted(projectname,projectname,projectname,policynode);
}
}