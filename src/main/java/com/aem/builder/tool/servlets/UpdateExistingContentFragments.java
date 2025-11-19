package com.aem.builder.tool.servlets;

import com.adobe.cq.dam.cfm.ContentElement;
import com.adobe.cq.dam.cfm.ContentFragment;
import com.adobe.cq.dam.cfm.ContentFragmentException;
import com.adobe.cq.dam.cfm.FragmentTemplate;
import com.aem.builder.tool.util.CFDateHelper;
import com.aem.builder.tool.util.ExcelDateConverter;
import com.google.gson.Gson;
import org.apache.commons.lang3.StringUtils;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.resource.*;
import org.apache.sling.api.servlets.HttpConstants;
import org.apache.sling.api.servlets.SlingAllMethodsServlet;
import org.apache.sling.api.servlets.ServletResolverConstants;
import org.osgi.framework.Constants;
import org.osgi.service.component.annotations.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jcr.Node;
import javax.jcr.Property;
import javax.jcr.RepositoryException;
import javax.jcr.Value;
import javax.servlet.Servlet;
import javax.servlet.http.Part;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.*;

@Component(
        service = Servlet.class,
        property = {
                Constants.SERVICE_DESCRIPTION + "=Update Existing Content Fragments from Excel",
                ServletResolverConstants.SLING_SERVLET_PATHS + "=/bin/updateCFs",
                ServletResolverConstants.SLING_SERVLET_METHODS + "=" + HttpConstants.METHOD_POST
        }
)
public class UpdateExistingContentFragments extends SlingAllMethodsServlet {

    private static final Logger LOGGER = LoggerFactory.getLogger(UpdateExistingContentFragments.class);

    @Override
    protected void doPost(SlingHttpServletRequest request, SlingHttpServletResponse response) {
        response.setContentType("application/json; charset=UTF-8");
        response.setCharacterEncoding("UTF-8");

        LOGGER.info("====== 🚀 Starting Content Fragment Update ======");

        String parentPath = request.getParameter("parentPath");
        String titleColumn = request.getParameter("selectfield");
        String modelType = request.getParameter("modelType");

        if (StringUtils.isBlank(parentPath) || StringUtils.isBlank(titleColumn) || StringUtils.isBlank(modelType)) {
            response.setStatus(400);
            try {
                response.getWriter().write("{\"error\":\"Missing required parameters\"}");
            }catch(Exception e){

            }
            return;
        }

        try {
            Part filePart = request.getPart("excel");
            if (filePart == null) {
                response.setStatus(400);
                response.getWriter().write("{\"error\":\"Excel file missing\"}");
                return;
            }

            try (InputStream inputStream = filePart.getInputStream();
                 Workbook workbook = new XSSFWorkbook(inputStream)) {

                Sheet sheet = workbook.getSheetAt(0);
                List<String> headers = new ArrayList<>();
                List<List<String>> rowsData = new ArrayList<>();

                for (Row row : sheet) {
                    List<String> rowData = new ArrayList<>();
                    for (Cell cell : row) {
                        String value = "";

                        switch (cell.getCellType()) {
                            case STRING:
                                value = cell.getStringCellValue().trim();
                                break;

                            case NUMERIC:
                                if (DateUtil.isCellDateFormatted(cell)) {
                                    // Convert Excel date to AEM-compatible timestamp
                                    Date date = cell.getDateCellValue();
                                    value = new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX").format(date);
                                } else {
                                    value = String.valueOf(cell.getNumericCellValue());
                                }
                                break;

                            case BOOLEAN:
                                value = String.valueOf(cell.getBooleanCellValue());
                                break;

                            case FORMULA:
                                try {
                                    value = cell.getStringCellValue();
                                } catch (IllegalStateException e) {
                                    if (DateUtil.isCellDateFormatted(cell)) {
                                        Date date = cell.getDateCellValue();
                                        value = new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX").format(date);
                                    } else {
                                        value = String.valueOf(cell.getNumericCellValue());
                                    }
                                }
                                break;

                            default:
                                value = "";
                                break;
                        }

                        rowData.add(value.trim());
                    }
                    if (row.getRowNum() == 0) headers = rowData;
                    else rowsData.add(rowData);
                }

                ResourceResolver resolver = request.getResourceResolver();
                logExistingFragments(resolver, parentPath);

                int titleIndex = headers.indexOf(titleColumn);
                if (titleIndex == -1) titleIndex = 0;

                List<String> created = new ArrayList<>();
                List<String> updated = new ArrayList<>();
                List<String> skipped = new ArrayList<>();
                List<String> existingCFs = new ArrayList<>();
                for (List<String> rowData : rowsData) {
                    if (rowData.isEmpty()) continue;
                    //String cfName = sanitizeForJcr(cfTitleValue);
                    String cfTitleValue = rowData.get(titleIndex);
                    if (StringUtils.isEmpty(cfTitleValue)) continue;
                    cfTitleValue = cfTitleValue.replaceAll("\\.0$", "");
                    String cfName = sanitizeForJcr(cfTitleValue);
                    String cfPath = parentPath + "/" + cfName;

                    Resource cfRes = resolver.getResource(cfPath);

                    if (cfRes == null) {
                        LOGGER.info("🆕 Creating new CF: {}", cfName);
                        createContentFragment(resolver, parentPath, headers,  rowData.toArray(new String[0]), modelType, cfName);
                        created.add(cfName);
                        resolver.commit();
                        continue;
                    }

                    ContentFragment cf = cfRes.adaptTo(ContentFragment.class);
                    if (cf == null) {
                        LOGGER.warn("⚠️ Not a valid content fragment: {}", cfName);
                        skipped.add(cfName);
                        continue;
                    }


                    boolean changed = false;
//                    for (int i = 0; i < headers.size(); i++) {
//                        String header = headers.get(i);
//                        String newVal = safeValue(i < rowData.size() ? rowData.get(i) : "");
//                        ContentElement element = cf.getElement(header);
//                        if (element == null) continue;
//
//                        String oldVal = safeValue(element.getContent());
//                        boolean equal = valuesEqual(oldVal, newVal);
//
//                        LOGGER.info("🔍 Compare [{}]: old='{}' | new='{}' | equal={}", header, oldVal, newVal, equal);
//
//                        if (!equal) {
//                            element.setContent(newVal, element.getContentType());
//                            changed = true;
//                            LOGGER.info("✏️ Updated {} from '{}' → '{}'", header, oldVal, newVal);
//                        }
//                    }

                    Iterator<ContentElement> elements = cf.getElements();
                    Map<String, String> modelFieldTypes = getFieldTypeFromModel(resolver, "/conf/charger/settings/dam/cfm/models/" + modelType, "");

                    while (elements.hasNext()) {
                        ContentElement element = elements.next();
                        String elementNormalized = element.getName().replaceAll("[ _]", "").toLowerCase();

                        for (int i = 0; i < headers.size(); i++) {
                            String headerNormalized = headers.get(i).replaceAll("[ _]", "").toLowerCase();
                            if (i < rowData.size() && elementNormalized.equals(headerNormalized)) {
                                String oldValue = element.getContent();
                                String newValue = safeCellValue(rowData.get(i));

                                if (!valuesEqual(oldValue, newValue)) {

                                    // 🔍 get field type from model
                                    String fieldType = modelFieldTypes.getOrDefault(element.getName(), "string").toLowerCase();

                                    if ("date".equalsIgnoreCase(fieldType) || "datetime".equalsIgnoreCase(fieldType)) {
                                        try {
                                            Calendar cal = null;

                                            if (StringUtils.isNumeric(newValue)) {
                                                // Excel serial number (e.g., 38261.42916666667)
                                                cal = ExcelDateConverter.convertExcelSerialToCalendar(Double.parseDouble(newValue));
                                            } else {
                                                try {
                                                    // Try ISO format first
                                                    cal = javax.xml.bind.DatatypeConverter.parseDateTime(newValue);
                                                } catch (IllegalArgumentException e1) {
                                                    // Fallback to flexible parser
                                                    cal = parseFlexibleDate(newValue);
                                                }
                                            }

                                            if (cal != null) {
                                                CFDateHelper.setDateField(element, cal);
                                                LOGGER.info("✅ Date field '{}' updated with {}", element.getName(), cal.getTime());
                                            } else {
                                                LOGGER.warn("⚠️ Could not parse date '{}', saving raw text.", newValue);
                                                element.setContent(newValue, "text/plain");
                                            }

                                        } catch (Exception e) {
                                            LOGGER.warn("⚠️ Failed to parse date for '{}': {}", element.getName(), newValue, e);
                                            element.setContent(newValue, "text/plain");
                                        }
                                    }
                                    else {
                                        // ✅ Regular text, boolean, numeric, etc.
                                        element.setContent(newValue, element.getContentType());
                                    }

                                    changed = true;
                                    LOGGER.info("✅ Updated '{}' → '{}'", element.getName(), newValue);

                                }
                            }
                        }
                    }


                    if (changed) {
                        resolver.commit();
                        updated.add(cfName);
                        LOGGER.info("💾 Committed updates for {}", cfName);
                    } else {
                        skipped.add(cfName);
                        LOGGER.info("⏩ No changes for {}", cfName);
                    }
                }

                Map<String, Object> result = new HashMap<>();
                result.put("createFragments", created);
                result.put("updatedFragments", updated);
                result.put("skippedFragments", skipped);

                LOGGER.info("====== ✅ Update Summary ======");
                LOGGER.info("Created: {} | Updated: {} | Skipped: {}", created.size(), updated.size(), skipped.size());
                response.getWriter().write(new Gson().toJson(result));

            }
        } catch (Exception e) {
            LOGGER.error("❌ Error processing CF updates", e);
        }
    }

    // ✅ Helper: Normalize CF names
    private String normalizeCfName(String name) {
        if (StringUtils.isBlank(name)) return "unnamed";
        name = name.trim();
        if (name.matches("\\d+(\\.0+)?")) name = name.replaceAll("\\.0+$", "");
        name = name.replaceAll("[^a-zA-Z0-9_-]", "_").toLowerCase();
        return name;
    }

    // ✅ Helper: Log all existing CFs under the folder
    private void logExistingFragments(ResourceResolver resolver, String parentPath) {
        Resource folder = resolver.getResource(parentPath);
        if (folder == null) {
            LOGGER.warn("⚠️ Folder not found: {}", parentPath);
            return;
        }
        LOGGER.info("📂 Existing Content Fragments under {}", parentPath);
        for (Resource res : folder.getChildren()) {
            LOGGER.info("   → {}", res.getName());
        }
    }

    // ✅ Helper: Safe string normalization
    private String safeValue(String value) {
        return StringUtils.defaultString(value, "").trim();
    }

    // ✅ Helper: Compare values ignoring small formatting differences
    private boolean valuesEqual(String oldValue, String newValue) {
        if (StringUtils.isBlank(oldValue) && StringUtils.isBlank(newValue))
            return true;
        if (StringUtils.equalsIgnoreCase(oldValue.trim(), newValue.trim()))
            return true;

        // Normalize date-like values
        try {
            Calendar calOld = parseFlexibleDate(oldValue);
            Calendar calNew = parseFlexibleDate(newValue);
            if (calOld != null && calNew != null) {
                return calOld.getTimeInMillis() == calNew.getTimeInMillis();
            }
        } catch (Exception ignored) {
        }
        return false;
    }


    // ✅ Helper: Create new CF from model
    public void createContentFragment(ResourceResolver resolver, String parentPath,
                                      List<String> headerList, String[] data, String modelType, String cfName) {

        LOGGER.info("Creating content fragment: {}", cfName);

        try {
            Resource templateResource = resolver.getResource("/conf/charger/settings/dam/cfm/models/" + modelType);
            if (templateResource == null) {
                LOGGER.error("Template not found: {}", modelType);
                return;
            }

            FragmentTemplate fragmentTemplate = templateResource.adaptTo(FragmentTemplate.class);
            if (fragmentTemplate == null) {
                LOGGER.error("Failed to adapt template to FragmentTemplate: {}", modelType);
                return;
            }

            Resource existingCF = resolver.getResource(parentPath + "/" + cfName);
            ContentFragment cf;

            if (existingCF == null) {
                Resource parentResource = resolver.getResource(parentPath);
                if (parentResource == null) {
                    LOGGER.error("Parent path does not exist: {}", parentPath);
                    return;
                }
                cf = fragmentTemplate.createFragment(parentResource, cfName, cfName);
                LOGGER.info("✅ Created new content fragment: {}", cfName);
            } else {
                cf = existingCF.adaptTo(ContentFragment.class);
                LOGGER.info("Using existing content fragment: {}", cfName);
            }

            if (cf == null) return;

            String modelPath = "/conf/charger/settings/dam/cfm/models/" + modelType;
            Map<String, String> modelFieldTypes = getFieldTypeFromModel(resolver, modelPath, "");

            // Loop over CF elements and match Excel headers
            Iterator<ContentElement> elements = cf.getElements();
            while (elements.hasNext()) {
                ContentElement element = elements.next();
                String fieldName = element.getName();
                String normalizedElement = fieldName.replaceAll("[ _]", "").toLowerCase();

                for (int i = 0; i < headerList.size(); i++) {
                    String headerNormalized = headerList.get(i).replaceAll("[ _]", "").toLowerCase();
                    if (!normalizedElement.equals(headerNormalized) || i >= data.length) continue;

                    String fieldValue = data[i];
                    if (StringUtils.isBlank(fieldValue)) continue;

                    // Determine field type from model
                    String fieldType = modelFieldTypes.getOrDefault(fieldName, "string").toLowerCase();

                    LOGGER.info("Processing element '{}' (type={}) with value='{}'",
                            fieldName, fieldType, fieldValue);

                    try {
                        if ("date".equalsIgnoreCase(fieldType) || "datetime".equalsIgnoreCase(fieldType)) {
                            // --- Handle date or datetime ---
                            Calendar cal = null;
                            try {
                                if (StringUtils.isNumeric(fieldValue)) {
                                    // Excel serial numbers like 38261.42916666667
                                    cal = ExcelDateConverter.convertExcelSerialToCalendar(Double.parseDouble(fieldValue));
                                } else {
                                    // Try ISO first
                                    try {
                                        cal = javax.xml.bind.DatatypeConverter.parseDateTime(fieldValue);
                                    } catch (IllegalArgumentException e1) {
                                        // Fallback: try flexible formats (dd/MMM/yy, etc.)
                                        cal = parseFlexibleDate(fieldValue);
                                    }
                                }

                                if (cal != null) {
                                    CFDateHelper.setDateField(element, cal);
                                    LOGGER.info("✅ Date field '{}' updated with {}", fieldName, cal.getTime());
                                } else {
                                    LOGGER.warn("⚠️ Could not parse date for '{}': '{}'", fieldName, fieldValue);
                                    element.setContent(fieldValue, "text/plain");
                                }
                            } catch (Exception e) {
                                LOGGER.warn("⚠️ Failed to parse date for field '{}': {}", fieldName, fieldValue, e);
                                element.setContent(fieldValue, "text/plain");
                            }


                        }  else {
                            // Default fallback
                            element.setContent(fieldValue, "text/plain");
                        }

                    } catch (Exception e) {
                        LOGGER.error("Error setting content for element {}: {}", fieldName, e.getMessage(), e);
                    }
                    break; // matched header → stop inner loop
                }
            }

        } catch (ContentFragmentException e) {
            LOGGER.error("Error creating/updating content fragment", e);
        } catch (Exception e) {
            LOGGER.error("Unexpected error in createContentFragment", e);
        }
    }
    private String safeCellValue(String value) {
        return StringUtils.defaultString(value, "").trim();
    }

    private String sanitizeForJcr(String name) {
        return name.replaceAll("[^a-zA-Z0-9_-]", "_");
    }
    private Map<String,String> getFieldTypeFromModel(ResourceResolver resolver, String modelPath, String fieldName) {
        String type="";
        Map<String,String>modelvalues=new LinkedHashMap<>();

        try {
            Resource modelRoot = resolver.getResource(modelPath);
            if (modelRoot == null) {
                //  LOGGER.warn("⚠️ Model path not found: {}", modelPath);
                return null;
            }

            Iterator<Resource> items = modelRoot.listChildren();
            //  LOGGER.info("items {}",items);
            while (items.hasNext()) {
                Resource item = items.next();
                Node node = item.adaptTo(Node.class);

                Iterable<Resource>re=  item.getChildren();
                Iterator<Resource>res=re.iterator();
                while (res.hasNext()){
                    Resource child=res.next();
                    String val= child.getName();
                    Iterable<Resource>modelnodes=  child.getChildren();
                    Iterator<Resource>modenodes2=modelnodes.iterator();

                    while(modenodes2.hasNext()){
                        Resource chill=modenodes2.next();
                        Iterable<Resource>abc=  chill.getChildren();
                        Iterator<Resource>abcd=abc.iterator();
                        //  LOGGER.info("path 3: {}",chill.getPath());//cq:dialog
                        while (abcd.hasNext()){
                            Resource chill2= abcd.next();//content
                            Iterable<Resource>abc1=  chill2.getChildren();
                            Iterator<Resource>abcd1=abc1.iterator();
                            while (abcd1.hasNext()){
                                Resource items2= abcd1.next();//items
                                Iterable<Resource>abc2=  items2.getChildren();
                                Iterator<Resource>abcd2=abc2.iterator();
                                while (abcd2.hasNext()){
                                    Resource itemlist=abcd2.next();
                                    //   LOGGER.info("path4 : {}",itemlist.getPath());
                                    Node n=itemlist.adaptTo(Node.class);
                                    boolean check=n.hasProperty("name");
                                    Property p1=n.getProperty("name");
                                    String name=p1.getString();
                                    //   LOGGER.info("name :{}",p1.getString());


                                    boolean check2=n.hasProperty("metaType");
                                    //LOGGER.info("check1{}",check);
                                    //  LOGGER.info("check2{}",check2);
                                    //   LOGGER.info("finally got type :{}",n.getProperty("type").getString());
                                    Property prop=n.getProperty("metaType");
                                    if (prop.isMultiple()) {
                                        Value[] values = prop.getValues();
                                        type = values.length > 0 ? values[0].getString() : "string";
                                        //      LOGGER.info("finally got type : {}",type);

                                    } else {
                                        type = prop.getString();
                                        //     LOGGER.info("finally got type : {}",type);
                                    }
                                    modelvalues.put(name,type);
                                }
                            }
                        }
                    }

                }

            }

        } catch (RepositoryException e) {
            LOGGER.error("Error reading model type for field {}: {}", fieldName, e.getMessage(), e);

        }
        return modelvalues;

    }

    private Calendar parseFlexibleDate(String rawValue) {
        if (StringUtils.isBlank(rawValue)) return null;

        rawValue = rawValue.trim();

        // Remove excessive nanoseconds (.000000000 → .000)
        if (rawValue.matches(".*\\.\\d{6,}.*")) {
            rawValue = rawValue.replaceAll("\\.(\\d{3})\\d+", ".$1");
        }

        // Common flexible date patterns
        String[] patterns = {
                "dd/MMM/yy hh:mm:ss.SSS a",     // 01/OCT/04 09:40:00.000 AM
                "dd/MMM/yyyy hh:mm:ss.SSS a",   // 01/OCT/2004 09:40:00.000 AM
                "dd-MMM-yy hh:mm:ss.SSS a",     // 01-OCT-04 09:40:00.000 AM
                "dd-MMM-yyyy hh:mm:ss.SSS a",   // 01-OCT-2004 09:40:00.000 AM
                "dd/MMM/yy hh:mm:ss a",         // 01/OCT/04 09:40:00 AM
                "dd-MMM-yy hh:mm:ss a",         // 01-OCT-04 09:40:00 AM
                "dd/MM/yyyy HH:mm:ss",          // 10/1/2004 10:18:00
                "MM/dd/yyyy HH:mm:ss",          // US style
                "yyyy-MM-dd'T'HH:mm:ss.SSSXXX", // ISO format
                "yyyy-MM-dd HH:mm:ss",
                "yyyy/MM/dd HH:mm:ss"
        };

        for (String pattern : patterns) {
            try {
                SimpleDateFormat sdf = new SimpleDateFormat(pattern, Locale.ENGLISH);
                sdf.setLenient(true);
                Date date = sdf.parse(rawValue);
                Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("Asia/Kolkata"));
                cal.setTime(date);
                return cal;
            } catch (Exception ignored) {}
        }

        return null;
    }

}
