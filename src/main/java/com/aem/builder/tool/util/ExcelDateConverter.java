package com.aem.builder.tool.util;

import org.apache.poi.ss.usermodel.DateUtil;
import java.util.Calendar;
import java.util.Date;
import java.util.TimeZone;

public class ExcelDateConverter {

    public static Calendar convertExcelSerialToCalendar(double serial) {
        Date date = DateUtil.getJavaDate(serial, TimeZone.getTimeZone("Asia/Kolkata"));
        Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("Asia/Kolkata"));
        cal.setTime(date);
        return cal;
    }
}
