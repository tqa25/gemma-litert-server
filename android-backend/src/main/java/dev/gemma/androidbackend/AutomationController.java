package dev.gemma.androidbackend;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.util.Base64;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class AutomationController {
  private final Context context;
  private final ShizukuShellExecutor shell;
  private volatile boolean stopRequested;
  private volatile String activeRunId = "";
  private volatile String activeWorkflow = "";
  private volatile String lastError = "";

  AutomationController(Context context) {
    this.context = context.getApplicationContext();
    this.shell = new ShizukuShellExecutor();
  }

  String statusJson() {
    LinkedHashMap<String, Object> result = new LinkedHashMap<>();
    result.put("ok", true);
    result.put("shizuku_available", shell.isBinderAvailable());
    result.put("shizuku_permission_granted", shell.hasPermission());
    result.put("stop_requested", stopRequested);
    result.put("active_run_id", activeRunId);
    result.put("active_workflow", activeWorkflow);
    result.put("last_error", lastError);
    result.put("config", loadConfig().toJsonMap());
    return JsonUtil.object(result);
  }

  String stopJson() {
    stopRequested = true;
    LinkedHashMap<String, Object> result = base("stop");
    result.put("stop_requested", true);
    return JsonUtil.object(result);
  }

  String configJson() {
    LinkedHashMap<String, Object> result = base("config");
    result.put("config", loadConfig().toJsonMap());
    return JsonUtil.object(result);
  }

  String calibrateJson(String body) throws Exception {
    String key = requireString(body, "key");
    int x = JsonUtil.intValue(body, "x", -1);
    int y = JsonUtil.intValue(body, "y", -1);
    if (x < 1 || y < 1) throw new IllegalArgumentException("x and y must be positive");
    AutomationConfig config = loadConfig();
    if ("chrome_discover_first_article".equals(key)) {
      config.chromeDiscoverArticleX = x;
      config.chromeDiscoverArticleY = y;
    } else if ("gemini_summary_button".equals(key)) {
      config.geminiSummaryButtonX = x;
      config.geminiSummaryButtonY = y;
    } else if ("gemini_copy_button".equals(key)) {
      config.geminiCopyButtonX = x;
      config.geminiCopyButtonY = y;
    } else {
      throw new IllegalArgumentException("unknown calibration key: " + key);
    }
    saveConfig(config);
    LinkedHashMap<String, Object> result = base("calibrate");
    result.put("key", key);
    result.put("x", x);
    result.put("y", y);
    result.put("config", config.toJsonMap());
    return JsonUtil.object(result);
  }

  String tapJson(String body) throws Exception {
    int x = JsonUtil.intValue(body, "x", -1);
    int y = JsonUtil.intValue(body, "y", -1);
    if (x < 1 || y < 1) throw new IllegalArgumentException("x and y must be positive");
    AutomationShellResult shellResult = shell.runChecked("input tap " + x + " " + y, 10000);
    LinkedHashMap<String, Object> result = base("tap");
    result.put("duration_ms", shellResult.durationMs);
    result.put("x", x);
    result.put("y", y);
    return JsonUtil.object(result);
  }

  String swipeJson(String body) throws Exception {
    int x1 = JsonUtil.intValue(body, "x1", -1);
    int y1 = JsonUtil.intValue(body, "y1", -1);
    int x2 = JsonUtil.intValue(body, "x2", -1);
    int y2 = JsonUtil.intValue(body, "y2", -1);
    int duration = JsonUtil.intValue(body, "duration_ms", 400);
    if (x1 < 0 || y1 < 0 || x2 < 0 || y2 < 0) throw new IllegalArgumentException("swipe coordinates are required");
    AutomationShellResult shellResult = shell.runChecked(
        "input swipe " + x1 + " " + y1 + " " + x2 + " " + y2 + " " + duration,
        15000);
    LinkedHashMap<String, Object> result = base("swipe");
    result.put("duration_ms", shellResult.durationMs);
    return JsonUtil.object(result);
  }

  String keyJson(String action, String keyCommand) throws Exception {
    AutomationShellResult shellResult = shell.runChecked(keyCommand, 10000);
    LinkedHashMap<String, Object> result = base(action);
    result.put("duration_ms", shellResult.durationMs);
    return JsonUtil.object(result);
  }

  String waitJson(String body) throws Exception {
    int ms = JsonUtil.intValue(body, "ms", 1000);
    if (ms < 1 || ms > 120000) throw new IllegalArgumentException("ms must be between 1 and 120000");
    Thread.sleep(ms);
    LinkedHashMap<String, Object> result = base("wait");
    result.put("duration_ms", ms);
    return JsonUtil.object(result);
  }

  String currentAppJson() throws Exception {
    String current = currentPackage();
    LinkedHashMap<String, Object> result = base("current-app");
    result.put("package", current);
    return JsonUtil.object(result);
  }

  String screenshotJson() throws Exception {
    AutomationShellResult shellResult = shell.runChecked("screencap -p", 20000);
    LinkedHashMap<String, Object> result = base("screenshot");
    result.put("duration_ms", shellResult.durationMs);
    result.put("image_base64", Base64.encodeToString(shellResult.stdout, Base64.NO_WRAP));
    result.put("image_bytes", shellResult.stdout.length);
    return JsonUtil.object(result);
  }

  String screenXmlJson() throws Exception {
    String xml = screenXml();
    LinkedHashMap<String, Object> result = base("screen-xml");
    result.put("xml", xml);
    result.put("xml_chars", xml.length());
    return JsonUtil.object(result);
  }

  String openAppJson(String body) throws Exception {
    String packageName = requireString(body, "package");
    AutomationShellResult shellResult = shell.runChecked(
        "monkey -p " + shellQuote(packageName) + " -c android.intent.category.LAUNCHER 1",
        15000);
    LinkedHashMap<String, Object> result = base("open-app");
    result.put("package", packageName);
    result.put("duration_ms", shellResult.durationMs);
    return JsonUtil.object(result);
  }

  String runWorkflowJson(String body) throws Exception {
    String workflow = requireString(body, "workflow");
    boolean debugCapture = JsonUtil.booleanValue(body, "debug_capture", false);
    if (!AutomationConfig.WORKFLOW_CHROME_DISCOVER_ONCE.equals(workflow)) {
      throw new IllegalArgumentException("unknown workflow: " + workflow);
    }
    return runChromeDiscoverGeminiOnce(debugCapture);
  }

  private String runChromeDiscoverGeminiOnce(boolean debugCapture) throws Exception {
    AutomationConfig config = loadConfig();
    if (!config.hasChromeArticleCoordinate()) throw new IllegalStateException("missing calibration: chrome_discover_first_article");
    if (!config.hasGeminiSummaryCoordinate()) throw new IllegalStateException("missing calibration: gemini_summary_button");
    if (!config.hasGeminiCopyCoordinate()) throw new IllegalStateException("missing calibration: gemini_copy_button");
    stopRequested = false;
    String runId = Instant.now().toString().replace(":", "").replace(".", "") + "-" + UUID.randomUUID();
    activeRunId = runId;
    activeWorkflow = AutomationConfig.WORKFLOW_CHROME_DISCOVER_ONCE;
    File runDir = new File(context.getFilesDir(), "automation_runs/" + runId);
    File articleDir = new File(runDir, "article_001");
    if (!articleDir.mkdirs() && !articleDir.isDirectory()) {
      throw new IllegalStateException("failed to create run directory: " + articleDir);
    }
    long started = System.currentTimeMillis();
    String previousClipboard = readClipboard();
    try {
      appendLog(runDir, "start", "workflow started");
      ensureAllowedChrome();
      if (debugCapture) capture(articleDir, "feed");
      checkRun(started, config);
      shell.runChecked("input tap " + config.chromeDiscoverArticleX + " " + config.chromeDiscoverArticleY, 10000);
      appendLog(runDir, "tap_article", "tapped Chrome Discover article");
      Thread.sleep(config.articleLoadMs);
      ensureAllowedChrome();
      if (debugCapture) capture(articleDir, "article_page");
      checkRun(started, config);
      shell.runChecked("input keyevent --longpress KEYCODE_HOME", 10000);
      appendLog(runDir, "open_gemini", "long-pressed HOME");
      Thread.sleep(config.geminiOpenMs);
      if (debugCapture) capture(articleDir, "gemini_before_summary");
      String geminiXml = safeScreenXml();
      if (!tapXmlText(geminiXml, "Tóm tắt trang") && !tapXmlText(geminiXml, "Summarize page")) {
        shell.runChecked("input tap " + config.geminiSummaryButtonX + " " + config.geminiSummaryButtonY, 10000);
        appendLog(runDir, "tap_summary_fallback", "tapped calibrated Gemini summary button");
      } else {
        appendLog(runDir, "tap_summary_xml", "tapped Gemini summary XML node");
      }
      Thread.sleep(config.geminiSummaryMs);
      if (debugCapture) capture(articleDir, "gemini_summary");
      String copyXml = safeScreenXml();
      if (!tapXmlContent(copyXml, "Copy") && !tapXmlContent(copyXml, "Sao chép")) {
        shell.runChecked("input tap " + config.geminiCopyButtonX + " " + config.geminiCopyButtonY, 10000);
        appendLog(runDir, "tap_copy_fallback", "tapped calibrated copy button");
      } else {
        appendLog(runDir, "tap_copy_xml", "tapped copy XML node");
      }
      Thread.sleep(1000);
      String summary = readClipboard();
      if (summary.equals(previousClipboard) || summary.trim().length() < 20) {
        throw new IllegalStateException("clipboard did not contain a new summary");
      }
      writeText(new File(articleDir, "summary.txt"), summary);
      writeText(new File(articleDir, "metadata.json"), metadataJson(runId, summary, debugCapture, started));
      writeText(new File(runDir, "run.json"), runJson(runId, "success", "", started));
      appendLog(runDir, "saved", "summary saved");
      LinkedHashMap<String, Object> result = base("run-workflow");
      result.put("run_id", runId);
      result.put("workflow", AutomationConfig.WORKFLOW_CHROME_DISCOVER_ONCE);
      result.put("summary_chars", summary.length());
      result.put("run_dir", runDir.getAbsolutePath());
      return JsonUtil.object(result);
    } catch (Exception e) {
      lastError = e.getMessage();
      safeErrorCapture(articleDir, e);
      writeText(new File(runDir, "run.json"), runJson(runId, "error", e.getMessage(), started));
      appendLog(runDir, "error", e.getMessage());
      throw e;
    } finally {
      activeRunId = "";
      activeWorkflow = "";
    }
  }

  private void ensureAllowedChrome() throws Exception {
    String packageName = currentPackage();
    if (!"com.android.chrome".equals(packageName)) {
      throw new IllegalStateException("expected Chrome package, got: " + packageName);
    }
  }

  private void checkRun(long startedMs, AutomationConfig config) {
    if (stopRequested) throw new IllegalStateException("automation stop requested");
    long maxMs = config.maxRunMinutes * 60_000L;
    if (System.currentTimeMillis() - startedMs > maxMs) {
      throw new IllegalStateException("workflow exceeded max_run_minutes");
    }
  }

  private boolean tapXmlText(String xml, String text) throws Exception {
    int[] bounds = findBounds(xml, "text", text);
    if (bounds == null) return false;
    shell.runChecked("input tap " + center(bounds[0], bounds[2]) + " " + center(bounds[1], bounds[3]), 10000);
    return true;
  }

  private boolean tapXmlContent(String xml, String text) throws Exception {
    int[] bounds = findBounds(xml, "content-desc", text);
    if (bounds == null) bounds = findBounds(xml, "text", text);
    if (bounds == null) return false;
    shell.runChecked("input tap " + center(bounds[0], bounds[2]) + " " + center(bounds[1], bounds[3]), 10000);
    return true;
  }

  private static int center(int a, int b) {
    return a + ((b - a) / 2);
  }

  private static int[] findBounds(String xml, String attr, String contains) {
    Pattern pattern = Pattern.compile(attr + "=\"([^\"]*" + Pattern.quote(contains) + "[^\"]*)\"[^>]*bounds=\"\\[(\\d+),(\\d+)\\]\\[(\\d+),(\\d+)\\]\"");
    Matcher matcher = pattern.matcher(xml);
    if (!matcher.find()) return null;
    return new int[] {
        Integer.parseInt(matcher.group(2)),
        Integer.parseInt(matcher.group(3)),
        Integer.parseInt(matcher.group(4)),
        Integer.parseInt(matcher.group(5))
    };
  }

  private String currentPackage() throws Exception {
    AutomationShellResult result = shell.runChecked(
        "dumpsys window | grep -E 'mCurrentFocus|mFocusedApp|mTopResumedActivity|topResumedActivity' || true",
        10000);
    String out = result.stdoutUtf8();
    Matcher matcher = Pattern.compile(" ([a-zA-Z0-9_.]+)/(?:[a-zA-Z0-9_.$]+)").matcher(out);
    String found = "";
    while (matcher.find()) {
      found = matcher.group(1);
    }
    if (found.isEmpty()) {
      matcher = Pattern.compile("u\\d+ ([a-zA-Z0-9_.]+)/").matcher(out);
      if (matcher.find()) found = matcher.group(1);
    }
    return found.isEmpty() ? "unknown" : found;
  }

  private String screenXml() throws Exception {
    AutomationShellResult result = shell.runChecked(
        "tmp=/sdcard/window-gemma-automation.xml; uiautomator dump --compressed \"$tmp\" >/dev/null && cat \"$tmp\"; rm -f \"$tmp\"",
        30000);
    return result.stdoutUtf8();
  }

  private String safeScreenXml() {
    try {
      return screenXml();
    } catch (Exception e) {
      return "";
    }
  }

  private void capture(File dir, String name) throws Exception {
    AutomationShellResult png = shell.runChecked("screencap -p", 20000);
    Files.write(new File(dir, name + ".png").toPath(), png.stdout);
    writeText(new File(dir, name + ".xml"), safeScreenXml());
  }

  private void safeErrorCapture(File dir, Exception error) {
    try {
      capture(dir, "error");
      LinkedHashMap<String, Object> row = new LinkedHashMap<>();
      row.put("error", error.getMessage());
      row.put("timestamp", Instant.now().toString());
      writeText(new File(dir, "error.json"), JsonUtil.object(row));
    } catch (Exception ignored) {
    }
  }

  private String readClipboard() {
    ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
    if (clipboard == null || !clipboard.hasPrimaryClip()) return "";
    ClipData clip = clipboard.getPrimaryClip();
    if (clip == null || clip.getItemCount() == 0) return "";
    CharSequence text = clip.getItemAt(0).coerceToText(context);
    return text == null ? "" : text.toString();
  }

  private AutomationConfig loadConfig() {
    AutomationConfig config = new AutomationConfig();
    File file = configFile();
    if (!file.exists()) return config;
    try {
      String json = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
      config.chromeDiscoverArticleX = JsonUtil.intValue(json, "chrome_discover_first_article_x", config.chromeDiscoverArticleX);
      config.chromeDiscoverArticleY = JsonUtil.intValue(json, "chrome_discover_first_article_y", config.chromeDiscoverArticleY);
      config.geminiSummaryButtonX = JsonUtil.intValue(json, "gemini_summary_button_x", config.geminiSummaryButtonX);
      config.geminiSummaryButtonY = JsonUtil.intValue(json, "gemini_summary_button_y", config.geminiSummaryButtonY);
      config.geminiCopyButtonX = JsonUtil.intValue(json, "gemini_copy_button_x", config.geminiCopyButtonX);
      config.geminiCopyButtonY = JsonUtil.intValue(json, "gemini_copy_button_y", config.geminiCopyButtonY);
      config.articleLoadMs = JsonUtil.intValue(json, "article_load_ms", config.articleLoadMs);
      config.geminiOpenMs = JsonUtil.intValue(json, "gemini_open_ms", config.geminiOpenMs);
      config.geminiSummaryMs = JsonUtil.intValue(json, "gemini_summary_ms", config.geminiSummaryMs);
      config.maxRunMinutes = JsonUtil.intValue(json, "max_run_minutes", config.maxRunMinutes);
    } catch (Exception e) {
      lastError = "failed to load automation config: " + e.getMessage();
    }
    return config;
  }

  private void saveConfig(AutomationConfig config) throws Exception {
    LinkedHashMap<String, Object> row = new LinkedHashMap<>();
    row.put("chrome_discover_first_article_x", config.chromeDiscoverArticleX);
    row.put("chrome_discover_first_article_y", config.chromeDiscoverArticleY);
    row.put("gemini_summary_button_x", config.geminiSummaryButtonX);
    row.put("gemini_summary_button_y", config.geminiSummaryButtonY);
    row.put("gemini_copy_button_x", config.geminiCopyButtonX);
    row.put("gemini_copy_button_y", config.geminiCopyButtonY);
    row.put("article_load_ms", config.articleLoadMs);
    row.put("gemini_open_ms", config.geminiOpenMs);
    row.put("gemini_summary_ms", config.geminiSummaryMs);
    row.put("max_run_minutes", config.maxRunMinutes);
    writeText(configFile(), JsonUtil.object(row));
  }

  private File configFile() {
    return new File(context.getFilesDir(), "automation_config.json");
  }

  private void appendLog(File runDir, String event, String message) throws Exception {
    LinkedHashMap<String, Object> row = new LinkedHashMap<>();
    row.put("timestamp", Instant.now().toString());
    row.put("event", event);
    row.put("message", message == null ? "" : message);
    File file = new File(runDir, "workflow_log.jsonl");
    file.getParentFile().mkdirs();
    Files.write(
        file.toPath(),
        (JsonUtil.object(row) + "\n").getBytes(StandardCharsets.UTF_8),
        java.nio.file.StandardOpenOption.CREATE,
        java.nio.file.StandardOpenOption.APPEND);
  }

  private String metadataJson(String runId, String summary, boolean debugCapture, long started) throws Exception {
    LinkedHashMap<String, Object> row = new LinkedHashMap<>();
    row.put("workflow", AutomationConfig.WORKFLOW_CHROME_DISCOVER_ONCE);
    row.put("run_id", runId);
    row.put("article_index", 1);
    row.put("summary_backend", "gemini_overlay");
    row.put("started_at_ms", started);
    row.put("finished_at_ms", System.currentTimeMillis());
    row.put("current_package_after", currentPackage());
    row.put("clipboard_chars", summary.length());
    row.put("debug_capture", debugCapture);
    return JsonUtil.object(row);
  }

  private String runJson(String runId, String status, String error, long started) {
    LinkedHashMap<String, Object> row = new LinkedHashMap<>();
    row.put("run_id", runId);
    row.put("workflow", AutomationConfig.WORKFLOW_CHROME_DISCOVER_ONCE);
    row.put("status", status);
    row.put("error", error == null ? "" : error);
    row.put("started_at_ms", started);
    row.put("finished_at_ms", System.currentTimeMillis());
    return JsonUtil.object(row);
  }

  private static LinkedHashMap<String, Object> base(String action) {
    LinkedHashMap<String, Object> result = new LinkedHashMap<>();
    result.put("ok", true);
    result.put("request_id", UUID.randomUUID().toString());
    result.put("action", action);
    return result;
  }

  private static String requireString(String body, String key) {
    String value = JsonUtil.stringValue(body, key);
    if (value == null || value.trim().isEmpty()) throw new IllegalArgumentException("missing required field: " + key);
    return value;
  }

  private static String shellQuote(String value) {
    return "'" + value.replace("'", "'\"'\"'") + "'";
  }

  private static void writeText(File file, String value) throws Exception {
    File parent = file.getParentFile();
    if (parent != null) parent.mkdirs();
    Files.write(file.toPath(), value.getBytes(StandardCharsets.UTF_8));
  }
}
