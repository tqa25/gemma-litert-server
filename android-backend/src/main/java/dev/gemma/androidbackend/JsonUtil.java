package dev.gemma.androidbackend;

import java.util.Iterator;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class JsonUtil {
  private JsonUtil() {}

  static String stringValue(String json, String key) {
    Pattern pattern = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*\"((?:\\\\.|[^\"])*)\"");
    Matcher matcher = pattern.matcher(json);
    return matcher.find() ? unescape(matcher.group(1)) : null;
  }

  static int intValue(String json, String key, int defaultValue) {
    String value = numberValue(json, key);
    return value == null ? defaultValue : Integer.parseInt(value);
  }

  static double doubleValue(String json, String key, double defaultValue) {
    String value = numberValue(json, key);
    return value == null ? defaultValue : Double.parseDouble(value);
  }

  static String object(Map<String, ?> values) {
    StringBuilder out = new StringBuilder("{");
    Iterator<? extends Map.Entry<String, ?>> it = values.entrySet().iterator();
    while (it.hasNext()) {
      Map.Entry<String, ?> entry = it.next();
      out.append(quote(entry.getKey())).append(":").append(value(entry.getValue()));
      if (it.hasNext()) out.append(",");
    }
    return out.append("}").toString();
  }

  static String error(String code, String message) {
    java.util.LinkedHashMap<String, Object> row = new java.util.LinkedHashMap<>();
    row.put("error", code);
    row.put("message", message == null ? "" : message);
    return object(row);
  }

  private static String numberValue(String json, String key) {
    Pattern pattern = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*(-?\\d+(?:\\.\\d+)?)");
    Matcher matcher = pattern.matcher(json);
    return matcher.find() ? matcher.group(1) : null;
  }

  private static String value(Object value) {
    if (value == null) return "null";
    if (value instanceof String) return quote((String) value);
    if (value instanceof Number || value instanceof Boolean) return value.toString();
    if (value instanceof Map<?, ?>) {
      @SuppressWarnings("unchecked")
      Map<String, ?> map = (Map<String, ?>) value;
      return object(map);
    }
    return quote(value.toString());
  }

  private static String quote(String value) {
    return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\"";
  }

  private static String unescape(String value) {
    return value.replace("\\n", "\n").replace("\\\"", "\"").replace("\\\\", "\\");
  }
}
