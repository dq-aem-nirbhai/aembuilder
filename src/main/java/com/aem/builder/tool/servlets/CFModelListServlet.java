package com.aem.builder.tool.servlets;

import com.aem.builder.tool.service.ContentFragmentModelService;
import com.google.gson.Gson;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.servlets.HttpConstants;
import org.apache.sling.api.servlets.SlingAllMethodsServlet;
import org.apache.sling.api.servlets.ServletResolverConstants;
import org.osgi.framework.Constants;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

import javax.servlet.Servlet;
import java.io.IOException;
import java.util.List;
import java.util.Map;

@Component(
        service = Servlet.class,
        property = {
                Constants.SERVICE_DESCRIPTION + "=CF Model List Servlet",
                ServletResolverConstants.SLING_SERVLET_PATHS + "=/bin/getCFModels",
                ServletResolverConstants.SLING_SERVLET_METHODS + "=" + HttpConstants.METHOD_GET
        }
)
public class CFModelListServlet extends SlingAllMethodsServlet {

    @Reference
    private ContentFragmentModelService modelService;

    @Override
    protected void doGet(SlingHttpServletRequest request, SlingHttpServletResponse response) throws IOException {
        response.setContentType("application/json");
        List<Map<String, String>> models = modelService.getAllModels(request.getResourceResolver());
        response.getWriter().write(new Gson().toJson(models));
    }
}
