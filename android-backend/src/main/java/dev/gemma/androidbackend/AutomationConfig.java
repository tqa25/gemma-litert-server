package dev.gemma.androidbackend;

import java.util.LinkedHashMap;

final class AutomationConfig {
  static final String WORKFLOW_CHROME_DISCOVER_ONCE = "chrome-discover-gemini-summary-once";
  static final String WORKFLOW_CHROME_READING_GEMMA_ONCE = "chrome-discover-reading-gemma-summary-once";
  static final String[] DEFAULT_ALLOWED_PACKAGES = new String[] {
      "dev.gemma.androidbackend",
      "com.android.chrome",
      "com.google.android.googlequicksearchbox"
  };

  int chromeDiscoverArticleX = 0;
  int chromeDiscoverArticleY = 0;
  int geminiSummaryButtonX = 0;
  int geminiSummaryButtonY = 0;
  int geminiCopyButtonX = 0;
  int geminiCopyButtonY = 0;
  int chromeMenuButtonX = 0;
  int chromeMenuButtonY = 0;
  int chromeShowReadingModeX = 0;
  int chromeShowReadingModeY = 0;
  int articleLoadMs = 5000;
  int geminiOpenMs = 3000;
  int geminiSummaryMs = 10000;
  int chromeMenuOpenMs = 300;
  int readingModeLoadMs = 1500;
  int readingTextMaxScrolls = 6;
  int readingTextMinChars = 500;
  int maxRunMinutes = 3;

  LinkedHashMap<String, Object> toJsonMap() {
    LinkedHashMap<String, Object> root = new LinkedHashMap<>();
    LinkedHashMap<String, Object> coordinates = new LinkedHashMap<>();
    coordinates.put("chrome_discover_first_article", point(chromeDiscoverArticleX, chromeDiscoverArticleY));
    coordinates.put("gemini_summary_button", point(geminiSummaryButtonX, geminiSummaryButtonY));
    coordinates.put("gemini_copy_button", point(geminiCopyButtonX, geminiCopyButtonY));
    coordinates.put("chrome_menu_button", point(chromeMenuButtonX, chromeMenuButtonY));
    coordinates.put("chrome_show_reading_mode", point(chromeShowReadingModeX, chromeShowReadingModeY));
    LinkedHashMap<String, Object> timing = new LinkedHashMap<>();
    timing.put("article_load_ms", articleLoadMs);
    timing.put("gemini_open_ms", geminiOpenMs);
    timing.put("gemini_summary_ms", geminiSummaryMs);
    timing.put("chrome_menu_open_ms", chromeMenuOpenMs);
    timing.put("reading_mode_load_ms", readingModeLoadMs);
    timing.put("reading_text_max_scrolls", readingTextMaxScrolls);
    timing.put("reading_text_min_chars", readingTextMinChars);
    timing.put("max_run_minutes", maxRunMinutes);
    root.put("workflow", WORKFLOW_CHROME_READING_GEMMA_ONCE);
    root.put("fallback_workflow", WORKFLOW_CHROME_DISCOVER_ONCE);
    root.put("allowed_packages", String.join(",", DEFAULT_ALLOWED_PACKAGES));
    root.put("coordinates", coordinates);
    root.put("timing", timing);
    return root;
  }

  boolean hasChromeArticleCoordinate() {
    return chromeDiscoverArticleX > 0 && chromeDiscoverArticleY > 0;
  }

  boolean hasGeminiSummaryCoordinate() {
    return geminiSummaryButtonX > 0 && geminiSummaryButtonY > 0;
  }

  boolean hasGeminiCopyCoordinate() {
    return geminiCopyButtonX > 0 && geminiCopyButtonY > 0;
  }

  boolean hasChromeMenuCoordinate() {
    return chromeMenuButtonX > 0 && chromeMenuButtonY > 0;
  }

  boolean hasChromeReadingModeCoordinate() {
    return chromeShowReadingModeX > 0 && chromeShowReadingModeY > 0;
  }

  private static LinkedHashMap<String, Object> point(int x, int y) {
    LinkedHashMap<String, Object> point = new LinkedHashMap<>();
    point.put("x", x);
    point.put("y", y);
    return point;
  }
}
