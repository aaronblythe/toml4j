package com.leacox.toml4j;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.TimeZone;

/**
 * Helper class for handling ISO 8601 strings of the following format:
 * "2008-03-01T13:00:00+01:00". It also supports parsing the "Z" timezone.
 * 
 * From Stackoverflow:
 * http://stackoverflow.com/questions/2201925/converting-iso8601
 * -compliant-string-to-java-util-date
 */
public final class ISO8601 {
	/** Transform Calendar to ISO 8601 string. */
	public static String fromCalendar(final Calendar calendar) {
		Date date = calendar.getTime();
		DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ");
		dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
		String formatted = dateFormat.format(date);
		return formatted.replace("+0000", "Z");
	}

	/** Get current date and time formatted as ISO 8601 string. */
	public static String now() {
		return fromCalendar(GregorianCalendar.getInstance());
	}

	/** Transform ISO 8601 string to Calendar. */
	public static Calendar toCalendar(final String iso8601String) throws ParseException {
		Calendar calendar = GregorianCalendar.getInstance();
		String s = iso8601String.replace("Z", "+00:00");
		try {
			s = s.substring(0, 22) + s.substring(23);
		} catch (IndexOutOfBoundsException e) {
			throw new ParseException("Invalid length", 0);
		}
		Date date = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ").parse(s);
		calendar.setTime(date);
		calendar.setTimeZone(TimeZone.getTimeZone("UTC"));
		return calendar;
	}
}