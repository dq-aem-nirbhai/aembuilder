package com.aem.builder.tool.util;

import com.adobe.cq.dam.cfm.ContentElement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Calendar;

public class CFDateHelper {
    private static final Logger LOGGER = LoggerFactory.getLogger(CFDateHelper.class);

    public static void setDateField(ContentElement element, Calendar cal) {
        try {
            if (element == null || cal == null) {
                LOGGER.warn("Element or calendar is null, skipping date set.");
                return;
            }

            // Convert to epoch milliseconds
            long timestamp = cal.getTimeInMillis();

            // AEM expects timestamps as strings for date fields
            element.setContent(String.valueOf(timestamp), "text/plain");

            LOGGER.info("✅ Date field '{}' set as timestamp: {}", element.getName(), timestamp);
        } catch (Exception e) {
            LOGGER.error("❌ Failed to set date field for element {}", element != null ? element.getName() : "null", e);
        }
    }
}
