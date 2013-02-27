package com.leacox.toml4j;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.leacox.toml4j.node.TomlArrayNode;
import com.leacox.toml4j.node.TomlBooleanNode;
import com.leacox.toml4j.node.TomlDateTimeNode;
import com.leacox.toml4j.node.TomlFloatNode;
import com.leacox.toml4j.node.TomlHashNode;
import com.leacox.toml4j.node.TomlIntegerNode;
import com.leacox.toml4j.node.TomlNode;
import com.leacox.toml4j.node.TomlStringNode;

public class TomlParser {
	private static final Matcher commentMatcher = Pattern.compile("#.*").matcher("");
	private static final Matcher keyGroupExpressionMatcher = Pattern.compile("\\[(\\w+(\\.\\w+)*)]").matcher("");
	private static final Matcher valueExpressionMatcher = Pattern.compile("([^\\s]\\w+)\\s*=(.+)").matcher("");

	private static final Matcher stringValueMatcher = Pattern.compile("^\".*\"$").matcher("");
	private static final Matcher integerValueMatcher = Pattern.compile("^-?\\d+$").matcher("");
	private static final Matcher floatValueMatcher = Pattern.compile("^-?\\d+\\.\\d+?$").matcher("");
	private static final Matcher booleanValueMatcher = Pattern.compile("^(true|false)$").matcher("");
	private static final Matcher dateTimeValueMatcher = Pattern.compile("^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}Z")
			.matcher("");
	private static final Matcher arrayValueMatcher = Pattern.compile("\\[(.*)\\]").matcher("");

	public TomlNode parse(String tomlString) throws IOException {
		TomlHashNode rootNode = new TomlHashNode();
		// TODO: Need TomlContainerNode
		TomlHashNode currentNode = rootNode;
		BufferedReader reader = new BufferedReader(new StringReader(tomlString));
		try {
			String line;
			while ((line = reader.readLine()) != null) {
				stripCommentAndWhitespace(line);

				if (keyGroupExpressionMatcher.reset(line).matches()) {
					String keyGroupPath = keyGroupExpressionMatcher.group(1);
					currentNode = rootNode;
					for (String keyGroup : keyGroupPath.split("\\.")) {
						TomlNode existingNode = currentNode.get(keyGroup);
						if (existingNode != null && !existingNode.isHash()) {
							throw new ParseException("Duplicate key found: " + keyGroupPath);
						}

						if (existingNode == null) {
							existingNode = new TomlHashNode();
							currentNode.put(keyGroup, existingNode);
						}

						currentNode = (TomlHashNode) existingNode;
					}
				} else if (valueExpressionMatcher.reset(line).matches()) {
					String key = valueExpressionMatcher.group(1);
					String value = valueExpressionMatcher.group(2).trim();

					if (currentNode.contains(key)) {
						throw new ParseException("Duplicate key found");
					}

					// Parse out whole multiline array
					if (value.startsWith("[") && !value.endsWith("]")) {
						value = parseMultilineArray(value, reader);
					}

					currentNode.put(key, parseValue(value));
				} else {
					throw new ParseException("Invalid line: " + line);
				}
			}

			return rootNode;
		} finally {
			reader.close();
		}
	}

	private String parseMultilineArray(String firstLine, BufferedReader reader) throws IOException {
		StringBuilder singleLineArrayBuilder = new StringBuilder(firstLine);
		boolean arrayEndingFound = false;
		String line;
		while ((line = reader.readLine()) != null) {
			singleLineArrayBuilder.append(line);

			if (line.endsWith("]")) {
				arrayEndingFound = true;
				break;
			}
		}

		if (!arrayEndingFound) {
			throw new ParseException("Unclosed array");
		}

		return singleLineArrayBuilder.toString();
	}

	private TomlNode parseValue(String value) {
		if (stringValueMatcher.reset(value).matches()) {
			value = value.substring(1, value.length() - 1); // Remove quotes
			return TomlStringNode.valueOf(unescapeString(value));
		} else if (integerValueMatcher.reset(value).matches()) {
			return TomlIntegerNode.valueOf(Long.valueOf(value));
		} else if (booleanValueMatcher.reset(value).matches()) {
			return TomlBooleanNode.valueOf(Boolean.valueOf(value));
		} else if (floatValueMatcher.reset(value).matches()) {
			return TomlFloatNode.valueOf(Double.valueOf(value));
		} else if (dateTimeValueMatcher.reset(value).matches()) {
			try {
				return TomlDateTimeNode.valueOf(ISO8601.toCalendar(value));
			} catch (java.text.ParseException e) {
				throw new ParseException("Invalid date value: " + value);
			}
		} else if (arrayValueMatcher.reset(value).matches()) {
			return parseArrayValue(value);
		} else {
			throw new ParseException("Invalid value: " + value);
		}
	}

	private TomlNode parseArrayValue(String value) {
		// Remove surrounding brackets '[' and ']'
		value = value.substring(1, value.length() - 1);
		TomlArrayNode arrayNode = new TomlArrayNode();
		if (value.matches(".*(?:\\]),.*")) { // Nested arrays
			// Split with lookbehind to keep brackets
			for (String nestedValue : value.split("(?<=(?:\\])),")) {
				TomlNode nestedArrayNode = parseValue(nestedValue.trim());
				arrayNode.add(nestedArrayNode);
			}
		} else {
			for (String arrayValue : value.split("\\,")) {
				TomlNode arrayValueNode = parseValue(arrayValue.trim());
				arrayNode.add(arrayValueNode);
			}
		}

		return arrayNode;
	}

	private void stripCommentAndWhitespace(String line) {
		commentMatcher.reset(line).replaceAll("").trim();
	}

	private String unescapeString(String value) {
		StringBuilder unescapedStringBuilder = new StringBuilder();
		for (int i = 0; i < value.length(); i++) {
			char character = value.charAt(i);
			if (character == '\\') {
				i++; // Advance to character following escape character
				if (i < value.length()) {
					character = value.charAt(i);
					switch (character) {
					case '0':
						unescapedStringBuilder.append('\u0000');
						break;
					case 't':
						unescapedStringBuilder.append('\t');
						break;
					case 'n':
						unescapedStringBuilder.append('\n');
						break;
					case 'r':
						unescapedStringBuilder.append('\r');
						break;
					case '"':
						unescapedStringBuilder.append('\"');
						break;
					case '\\':
						unescapedStringBuilder.append('\\');
						break;
					default:
						throw new ParseException("String value contains an invalid escape sequence: " + value);
					}
				} else {
					throw new ParseException("String value contains an invalid escape sequence: " + value);
				}
			} else {
				unescapedStringBuilder.append(character);
			}
		}

		return unescapedStringBuilder.toString();
	}
}