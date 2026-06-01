package dev.gemma.server.http;

import java.util.Iterator;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class JsonUtil {
  private JsonUtil() {}

  public static String stringValue(String json, String key) {
    Pattern pattern = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*\"((?:\\\\.|[^\"])*)\"");
    Matcher matcher = pattern.matcher(json);
    if (!matcher.find()) {
      return null;
    }
    return unescape(matcher.group(1));
  }

  public static int intValue(String json, String key, int defaultValue) {
    String number = numberValue(json, key);
    return number == null ? defaultValue : Integer.parseInt(number);
  }

  public static double doubleValue(String json, String key, double defaultValue) {
    String number = numberValue(json, key);
    return number == null ? defaultValue : Double.parseDouble(number);
  }

  public static String object(Map<String, ?> values) {
    StringBuilder out = new StringBuilder("{");
    Iterator<? extends Map.Entry<String, ?>> iterator = values.entrySet().iterator();
    while (iterator.hasNext()) {
      Map.Entry<String, ?> entry = iterator.next();
      out.append(quote(entry.getKey())).append(":").append(value(entry.getValue()));
      if (iterator.hasNext()) {
        out.append(",");
      }
    }
    return out.append("}").toString();
  }

  public static String error(String code, String message) {
    return object(Map.of("error", code, "message", message == null ? "" : message));
  }

  private static String value(Object value) {
    if (value == null) {
      return "null";
    }
    if (value instanceof String string) {
      return quote(string);
    }
    if (value instanceof Number || value instanceof Boolean) {
      return value.toString();
    }
    if (value instanceof Map<?, ?> map) {
      @SuppressWarnings("unchecked")
      Map<String, ?> typed = (Map<String, ?>) map;
      return object(typed);
    }
    return quote(value.toString());
  }

  private static String numberValue(String json, String key) {
    Pattern pattern = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*(-?\\d+(?:\\.\\d+)?)");
    Matcher matcher = pattern.matcher(json);
    return matcher.find() ? matcher.group(1) : null;
  }

  private static String quote(String value) {
    return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\"";
  }

  private static String unescape(String value) {
    return value.replace("\\n", "\n").replace("\\\"", "\"").replace("\\\\", "\\");
  }
}
