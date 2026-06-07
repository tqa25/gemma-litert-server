package dev.gemma.androidbackend;

import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.Date;
import java.util.Locale;

final class AutomationLog {
  private static final ArrayDeque<String> LINES = new ArrayDeque<>();
  private static final SimpleDateFormat TIME = new SimpleDateFormat("HH:mm:ss.SSS", Locale.US);

  private AutomationLog() {}

  static synchronized void add(String source, String message) {
    String line = TIME.format(new Date()) + " [" + source + "] " + (message == null ? "" : message);
    LINES.addLast(line);
    android.util.Log.i("GemmaAutomation", line);
  }

  static synchronized String text() {
    StringBuilder out = new StringBuilder();
    for (String line : LINES) {
      if (out.length() > 0) out.append('\n');
      out.append(line);
    }
    return out.toString();
  }

  static synchronized void clear() {
    LINES.clear();
  }
}
