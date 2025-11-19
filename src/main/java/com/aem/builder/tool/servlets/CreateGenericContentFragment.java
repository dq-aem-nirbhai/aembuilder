package com.aem.builder.tool.servlets;

import java.io.IOException;
import java.io.InputStream;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

import javax.jcr.Node;
import javax.jcr.Property;
import javax.jcr.RepositoryException;
import javax.jcr.Value;
import javax.servlet.Servlet;
import javax.servlet.http.Part;

import com.adobe.cq.dam.cfm.FragmentTemplate;
import com.adobe.cq.dam.cfm.ContentElement;
import com.adobe.cq.dam.cfm.ContentFragment;
import com.adobe.cq.dam.cfm.ContentFragmentException;

import com.aem.builder.tool.service.ExcelModelCompareService;
import com.aem.builder.tool.util.CFDateHelper;
import com.aem.builder.tool.util.ExcelDateConverter;
import org.apache.commons.lang3.StringUtils;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.servlets.HttpConstants;
import org.apache.sling.api.servlets.ServletResolverConstants;
import org.apache.sling.api.servlets.SlingAllMethodsServlet;
import org.osgi.framework.Constants;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.google.gson.stream.JsonWriter;


@Component(
        service = Servlet.class,
        property = {
                Constants.SERVICE_DESCRIPTION + "=" + CreateGenericContentFragment.SERVICE_DESCRIPTION,
                ServletResolverConstants.SLING_SERVLET_PATHS + "=" + "/bin/content/createContentFragment",
                ServletResolverConstants.SLING_SERVLET_METHODS + "=" + HttpConstants.METHOD_POST
        }
)
public class CreateGenericContentFragment extends SlingAllMethodsServlet {

    private static final Logger LOGGER = LoggerFactory.getLogger(CreateGenericContentFragment.class);
    private static final long serialVersionUID = 1L;
    protected static final String SERVICE_DESCRIPTION = "Create Generic Content Fragment from Excel";
    private static final String PARENT_PATH = "parentPath";
    private static final String MODEL_TYPE = "modelType";
    private static final String CSV = "excel";

    @Reference
    private ExcelModelCompareService excelModelCompareService;

    @Override
    protected void doPost(SlingHttpServletRequest request, SlingHttpServletResponse response) throws IOException {
        LOGGER.debug("Received POST request to create content fragment.");
        request.setCharacterEncoding("UTF-8");
        response.setContentType("application/json");
        response.setHeader("Cache-Control", "nocache");
        response.setCharacterEncoding("utf-8");

        JsonWriter jsout = null;
        ResourceResolver resolver = request.getResourceResolver();

        try {
            jsout = new JsonWriter(response.getWriter());
            jsout.beginObject();

            String parentPath = request.getParameter(PARENT_PATH);
            String modelType = request.getParameter(MODEL_TYPE);
            String titleColumn = request.getParameter("selectfield"); // user-selected column for CF title

            LOGGER.info("Parent Path: {}", parentPath);
            LOGGER.info("Model Type: {}", modelType);
            LOGGER.info("Selected CF title column: {}", titleColumn);
            String mode = request.getParameter("mode");
            if ("update".equalsIgnoreCase(mode)) {
                return; // Don't trigger create servlet if it's an update operation
            }

            if (StringUtils.isEmpty(parentPath) || StringUtils.isEmpty(modelType) || StringUtils.isEmpty(titleColumn)) {
                jsout.name("status").value("Parent path, model type, or title column missing");
                return; // finally will close the JSON object exactly once
            }

            Part filePart = request.getPart(CSV);
            if (filePart == null) {
                jsout.name("status").value("Excel file not provided");
                return;
            }

            // Compare Excel with CF model
            InputStream compareStream = filePart.getInputStream();
            String modelPath = "/conf/charger/settings/dam/cfm/models/" + modelType;
            String compareResult = excelModelCompareService.compareExcelWithModel(resolver, compareStream, modelPath);


            if (!"Matched".equals(compareResult)) {
                LOGGER.warn("Excel columns do not match the selected model: {}", modelType);
                jsout.name("status").value("Model mismatched for uploaded file");
                return;
            }

            // Reset input stream and read workbook
            InputStream fileContent = request.getPart(CSV).getInputStream();
            try (Workbook workbook = new XSSFWorkbook(fileContent)) {
                Sheet sheet = workbook.getSheetAt(0);

                List<String> headerList = new ArrayList<>();
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

                    if (row.getRowNum() == 0) {
                        headerList = rowData;
                    } else {
                        rowsData.add(rowData);
                    }
                }

                int titleIndex = headerList.indexOf(titleColumn);
                if (titleIndex == -1) {
                    LOGGER.warn("Selected title column not found in Excel headers. Using first column as fallback.");
                    titleIndex = 0;
                }

                int createdCount = 0;
                List<String> existingCFs = new ArrayList<>();
                List<String> newCFs = new ArrayList<>();

                // Step 1: Identify existing CFs (based on sanitized name)
                for (List<String> dataRow : rowsData) {
                    if (dataRow.isEmpty()) continue;

                    String cfTitleValue = dataRow.get(titleIndex);
                    if (StringUtils.isEmpty(cfTitleValue)) continue;

                    // clean numeric formatting like "326.0" -> "326"
                    cfTitleValue = cfTitleValue.replaceAll("\\.0$", "");
                    String cfName = sanitizeForJcr(cfTitleValue);

                    String cfPath = parentPath + "/" + cfName;
                    Resource existingResource = resolver.getResource(cfPath);

                    if (existingResource != null) {
                        existingCFs.add(cfName);
                    } else {
                        newCFs.add(cfName);
                    }
                }

                // Step 2: If all CFs already exist, report and stop
                if (newCFs.isEmpty()) {
                    String msg = "⚠️ All content fragments already exist for this Excel file.";
                    LOGGER.info(msg);
                    jsout.name("status").value(msg);
                    return;
                }

                // Step 3: Create only new CFs
                for (List<String> dataRow : rowsData) {
                    if (dataRow.isEmpty()) continue;

                    String cfTitleValue = dataRow.get(titleIndex);
                    if (StringUtils.isEmpty(cfTitleValue)) continue;

                    cfTitleValue = cfTitleValue.replaceAll("\\.0$", "");
                    String cfName = sanitizeForJcr(cfTitleValue);

                    if (existingCFs.contains(cfName)) {
                        LOGGER.info("Skipping existing CF: {}", cfName);
                        continue;
                    }

                    createContentFragment(resolver, parentPath, headerList, dataRow.toArray(new String[0]), modelType, cfName);
                    createdCount++;
                }

                // Step 4: Return status
                if (!existingCFs.isEmpty()) {
                    String msg = String.format(
                            "✅ Created %d new CF(s). ⚠️ Skipped %d existing CF(s).",
                            createdCount, existingCFs.size()
                    );
                    LOGGER.info(msg);
                    jsout.name("status").value(msg);
                } else {
                    jsout.name("status").value("✅ Created total of " + createdCount + " Content Fragments");
                }
            } catch (Exception e) {
                LOGGER.error("Error reading Excel workbook", e);
                jsout.name("status").value("Failed to read Excel file: " + e.getMessage());
                return;
            }

        } catch (Exception e) {
            LOGGER.error("Exception occurred while creating content fragments", e);
            // Try to write an error status if possible
            try {
                if (jsout != null) {
                    jsout.name("status").value("Failed to create Content Fragments: " + e.getMessage());
                } else {
                    response.getWriter().write("{\"status\":\"Failed to create Content Fragments: " + e.getMessage() + "\"}");
                }
            } catch (IOException ioEx) {
                LOGGER.error("Failed to send error response", ioEx);
            }
        } finally {
            // End JSON object exactly once and close writer
            try {
                if (jsout != null) {
                    jsout.endObject();
                    jsout.close();
                }
            } catch (Exception e) {
                LOGGER.error("Failed to properly close JsonWriter", e);
            }

            // Commit changes if any
            try {
                if (resolver != null && resolver.hasChanges()) {
                    resolver.commit();
                }
            } catch (Exception e) {
                LOGGER.error("Failed to commit changes to repository", e);
            }
        }
    }

    private String sanitizeForJcr(String name) {
        return name.replaceAll("[^a-zA-Z0-9_-]", "_");
    }
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












    private Calendar convertExcelSerialToCalendar(double serial) {
        // Excel epoch = 1899-12-30
        long millis = (long) ((serial - 25569) * 86400000L); // convert days to ms from 1970 epoch
        Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("Asia/Kolkata"));
        cal.setTimeInMillis(millis);
        return cal;
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
    //        long millis = (long) ((serial - 25569) * 86400000L); // convert days to ms from 1970 epoch
    //        Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("Asia/Kolkata"));
    //        cal.setTimeInMillis(millis);
    private Calendar parseFlexibleDate(String rawValue) {
        if (StringUtils.isBlank(rawValue)) return null;

        rawValue = rawValue.trim();

        // 🔹 Remove excess nanoseconds (.000000000 → .000)
        if (rawValue.matches(".*\\.\\d{6,}.*")) {
            rawValue = rawValue.replaceAll("\\.(\\d{3})\\d+", ".$1");
        }

        // 🔹 Supported formats (most common real-world ones)
        String[] patterns = {
                "dd/MMM/yy hh:mm:ss.SSS a",     // 01/OCT/04 09:40:00.000 AM
                "dd/MMM/yyyy hh:mm:ss.SSS a",   // 01/OCT/2004 09:40:00.000 AM
                "dd-MMM-yy hh:mm:ss.SSS a",     // 01-OCT-04 09:40:00.000 AM
                "dd-MMM-yyyy hh:mm:ss.SSS a",   // 01-OCT-2004 09:40:00.000 AM
                "dd/MMM/yy hh:mm:ss a",         // 01/OCT/04 09:40:00 AM
                "dd-MMM-yy hh:mm:ss a",         // 01-OCT-04 09:40:00 AM
                "dd/MM/yyyy HH:mm:ss",          // 10/1/2004 10:18:00
                "MM/dd/yyyy HH:mm:ss",          // US style fallback
                "yyyy-MM-dd'T'HH:mm:ss.SSSXXX", // ISO
                "yyyy-MM-dd HH:mm:ss",          // simple timestamp
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
            } catch (ParseException ignored) {}
        }

        return null;
    }



}