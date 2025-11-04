package com.aem.builder.tool.servlets;


import com.aem.builder.tool.service.ExcelModelCompareService;
import com.google.gson.Gson;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.servlets.HttpConstants;
import org.apache.sling.api.servlets.SlingAllMethodsServlet;
import org.apache.sling.api.servlets.ServletResolverConstants;
import org.osgi.framework.Constants;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.Servlet;
import javax.servlet.http.Part;
import java.io.IOException;

import java.io.InputStream;
import java.util.Set;



@Component(
        service = Servlet.class,
        property = {
                Constants.SERVICE_DESCRIPTION + "=CF Names List Servlet",
                ServletResolverConstants.SLING_SERVLET_PATHS + "=/bin/getCFNames",
                ServletResolverConstants.SLING_SERVLET_METHODS + "=" + HttpConstants.METHOD_POST
        }
)
public class ContentFragmentNames extends SlingAllMethodsServlet {
    private static final Logger LOGGER = LoggerFactory.getLogger(CreateGenericContentFragment.class);
    private static final String CSV = "excel";
    @Reference
    private ExcelModelCompareService excelModelCompareService;

    @Override
    protected void doPost(SlingHttpServletRequest request, SlingHttpServletResponse response) throws IOException {
        response.setContentType("application/json");
        LOGGER.info("servlet called");
        try {
            Part filePart = request.getPart(CSV);
            InputStream fileContent = filePart.getInputStream();
            Set<String> contentfragmentNames = excelModelCompareService.getExcelColumns(fileContent);
            LOGGER.info("contentfragmentNames : {}", contentfragmentNames);
            response.getWriter().write(new Gson().toJson(contentfragmentNames));

        }
        catch(Exception e){
            response.getWriter().write(e.getMessage());
        }
    }

}