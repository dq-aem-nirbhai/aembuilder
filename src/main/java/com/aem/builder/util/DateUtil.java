package com.aem.builder.util;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

public class DateUtil {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSXXX");

    public static String getCurrentJcrDate() {
        return "{Date}" + ZonedDateTime.now().format(FORMATTER);
    }
}
