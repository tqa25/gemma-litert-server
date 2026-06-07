package dev.gemma.androidbackend;

import android.Manifest;
import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.provider.OpenableColumns;
import android.view.Gravity;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ConnectException;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.json.JSONArray;
import org.json.JSONObject;

public final class MainActivity extends Activity {
  private static final int REQUEST_PICK_MODEL = 2001;
  private static final int REQUEST_PICK_OCR_IMAGE = 2002;
  private static final String DEFAULT_BACKEND_URL = "http://127.0.0.1:8765";
  private static final int MAX_OCR_HISTORY = 20;
  private static final String PREFS_NAME = "gemma_android_backend";
  private static final String PREF_BACKEND_URL = "backend_url";
  private static final String PREF_OCR_MODE = "ocr_mode";
  private static final String OCR_MODE_FAST = "fast";
  private static final String OCR_MODE_FULL = "full";
  private static final String FAST_PROMPT = "Extract visible text from this image. Return concise text.";
  private static final String FULL_PROMPT = "Extract all visible text from this image. Preserve line breaks. Return only the text.";

  private final ExecutorService copyExecutor = Executors.newSingleThreadExecutor();
  private final Handler diagnosticsHandler = new Handler(Looper.getMainLooper());
  private final Runnable diagnosticsRefresh = new Runnable() {
    @Override
    public void run() {
      refreshDiagnosticsStatus();
      refreshAutomationLog();
      diagnosticsHandler.postDelayed(this, 1000L);
    }
  };
  private TextView status;
  private TextView modelStatus;
  private TextView diagnosticsStatus;
  private TextView automationLogView;
  private TextView ocrImageStatus;
  private TextView ocrModeStatus;
  private TextView ocrResult;
  private TextView historyStatus;
  private EditText backendUrlInput;
  private Button startLiteRt;
  private Button selectOcrImageButton;
  private Button fastModeButton;
  private Button fullModeButton;
  private Button runOcrButton;
  private Button copyOcrButton;
  private LinearLayout historyList;
  private Uri selectedOcrImage;
  private String selectedOcrMode = OCR_MODE_FAST;
  private String lastOcrText = "";
  private boolean ocrRunning;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    requestNotificationPermission();
    SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
    selectedOcrMode = prefs.getString(PREF_OCR_MODE, OCR_MODE_FAST);
    if (!OCR_MODE_FULL.equals(selectedOcrMode)) selectedOcrMode = OCR_MODE_FAST;

    ScrollView scroll = new ScrollView(this);
    LinearLayout layout = new LinearLayout(this);
    layout.setOrientation(LinearLayout.VERTICAL);
    layout.setPadding(32, 32, 32, 32);
    layout.setGravity(Gravity.CENTER_HORIZONTAL);
    scroll.addView(layout);

    status = new TextView(this);
    status.setText("Gemma backend stopped\nPort: 127.0.0.1:8765");
    layout.addView(status);

    modelStatus = new TextView(this);
    layout.addView(modelStatus);

    diagnosticsStatus = new TextView(this);
    diagnosticsStatus.setPadding(0, 24, 0, 24);
    layout.addView(diagnosticsStatus);

    Button pickModel = new Button(this);
    pickModel.setText("Select/Copy Model File");
    pickModel.setOnClickListener(v -> openModelPicker());
    layout.addView(pickModel);

    Button startMock = new Button(this);
    startMock.setText("Start Mock Server");
    startMock.setOnClickListener(v -> startServer("mock"));
    layout.addView(startMock);

    startLiteRt = new Button(this);
    startLiteRt.setText("Start LiteRT GPU Server");
    startLiteRt.setOnClickListener(v -> startServer("litert-gpu"));
    layout.addView(startLiteRt);

    Button startLiteRtCpu = new Button(this);
    startLiteRtCpu.setText("Start LiteRT CPU Server");
    startLiteRtCpu.setOnClickListener(v -> startServer("litert-cpu"));
    layout.addView(startLiteRtCpu);

    Button stop = new Button(this);
    stop.setText("Stop Server");
    stop.setOnClickListener(v -> stopServer());
    layout.addView(stop);

    TextView automationTitle = new TextView(this);
    automationTitle.setPadding(0, 32, 0, 8);
    automationTitle.setText("Automation Overlay");
    layout.addView(automationTitle);

    Button overlayPermission = new Button(this);
    overlayPermission.setText("Enable Overlay Permission");
    overlayPermission.setOnClickListener(v -> openOverlayPermissionSettings());
    layout.addView(overlayPermission);

    Button showOverlay = new Button(this);
    showOverlay.setText("Show Floating Automation Controls");
    showOverlay.setOnClickListener(v -> showFloatingAutomation());
    layout.addView(showOverlay);

    Button hideOverlay = new Button(this);
    hideOverlay.setText("Hide Floating Automation Controls");
    hideOverlay.setOnClickListener(v -> hideFloatingAutomation());
    layout.addView(hideOverlay);

    TextView automationLogTitle = new TextView(this);
    automationLogTitle.setPadding(0, 24, 0, 8);
    automationLogTitle.setText("Automation Log");
    layout.addView(automationLogTitle);

    Button copyAutomationLog = new Button(this);
    copyAutomationLog.setText("Copy Automation Log");
    copyAutomationLog.setOnClickListener(v -> copyAutomationLog());
    layout.addView(copyAutomationLog);

    Button clearAutomationLog = new Button(this);
    clearAutomationLog.setText("Clear Automation Log");
    clearAutomationLog.setOnClickListener(v -> {
      AutomationLog.clear();
      refreshAutomationLog();
      status.setText("Automation log cleared.");
    });
    layout.addView(clearAutomationLog);

    automationLogView = new TextView(this);
    automationLogView.setTextIsSelectable(true);
    automationLogView.setText("No automation log yet.");
    layout.addView(automationLogView);

    TextView ocrTitle = new TextView(this);
    ocrTitle.setPadding(0, 32, 0, 8);
    ocrTitle.setText("OCR Runner");
    layout.addView(ocrTitle);

    backendUrlInput = new EditText(this);
    backendUrlInput.setSingleLine(true);
    backendUrlInput.setText(prefs.getString(PREF_BACKEND_URL, DEFAULT_BACKEND_URL));
    layout.addView(backendUrlInput);

    ocrImageStatus = new TextView(this);
    ocrImageStatus.setText("OCR image: none");
    layout.addView(ocrImageStatus);

    selectOcrImageButton = new Button(this);
    selectOcrImageButton.setText("Select OCR Image");
    selectOcrImageButton.setOnClickListener(v -> openOcrImagePicker());
    layout.addView(selectOcrImageButton);

    LinearLayout modeRow = new LinearLayout(this);
    modeRow.setOrientation(LinearLayout.HORIZONTAL);
    fastModeButton = new Button(this);
    fastModeButton.setText("Fast OCR");
    fastModeButton.setOnClickListener(v -> setOcrMode(OCR_MODE_FAST));
    modeRow.addView(fastModeButton);
    fullModeButton = new Button(this);
    fullModeButton.setText("Full OCR");
    fullModeButton.setOnClickListener(v -> setOcrMode(OCR_MODE_FULL));
    modeRow.addView(fullModeButton);
    layout.addView(modeRow);

    ocrModeStatus = new TextView(this);
    layout.addView(ocrModeStatus);

    runOcrButton = new Button(this);
    runOcrButton.setText("Run OCR");
    runOcrButton.setOnClickListener(v -> runSelectedOcr());
    layout.addView(runOcrButton);

    copyOcrButton = new Button(this);
    copyOcrButton.setText("Copy OCR Text");
    copyOcrButton.setOnClickListener(v -> copyOcrText());
    layout.addView(copyOcrButton);

    ocrResult = new TextView(this);
    ocrResult.setPadding(0, 16, 0, 0);
    ocrResult.setTextIsSelectable(true);
    ocrResult.setText("OCR result: none");
    layout.addView(ocrResult);

    TextView historyTitle = new TextView(this);
    historyTitle.setPadding(0, 32, 0, 8);
    historyTitle.setText("OCR History");
    layout.addView(historyTitle);

    historyStatus = new TextView(this);
    layout.addView(historyStatus);

    Button clearHistory = new Button(this);
    clearHistory.setText("Clear OCR History");
    clearHistory.setOnClickListener(v -> clearOcrHistory());
    layout.addView(clearHistory);

    historyList = new LinearLayout(this);
    historyList.setOrientation(LinearLayout.VERTICAL);
    layout.addView(historyList);

    setContentView(scroll);
    refreshModelStatus();
    refreshDiagnosticsStatus();
    refreshOcrModeStatus();
    setOcrRunning(false);
    refreshHistoryList();
  }

  @Override
  protected void onResume() {
    super.onResume();
    diagnosticsHandler.post(diagnosticsRefresh);
  }

  @Override
  protected void onPause() {
    saveOcrPreferences();
    diagnosticsHandler.removeCallbacks(diagnosticsRefresh);
    super.onPause();
  }

  @Override
  protected void onDestroy() {
    diagnosticsHandler.removeCallbacks(diagnosticsRefresh);
    copyExecutor.shutdownNow();
    super.onDestroy();
  }

  @Override
  protected void onActivityResult(int requestCode, int resultCode, Intent data) {
    super.onActivityResult(requestCode, resultCode, data);
    if (requestCode == REQUEST_PICK_MODEL && resultCode == RESULT_OK && data != null && data.getData() != null) {
      copyModelToPrivateStorage(data.getData());
      return;
    }
    if (requestCode == REQUEST_PICK_OCR_IMAGE && resultCode == RESULT_OK && data != null && data.getData() != null) {
      selectedOcrImage = data.getData();
      int flags = data.getFlags() & (Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
      try {
        getContentResolver().takePersistableUriPermission(selectedOcrImage, flags & Intent.FLAG_GRANT_READ_URI_PERMISSION);
      } catch (Exception ignored) {
        // Some providers do not support persistable grants. The current URI grant is enough for immediate OCR.
      }
      ocrImageStatus.setText("OCR image: " + displayName(selectedOcrImage));
    }
  }

  private void openModelPicker() {
    Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
    intent.addCategory(Intent.CATEGORY_OPENABLE);
    intent.setType("*/*");
    startActivityForResult(intent, REQUEST_PICK_MODEL);
  }

  private void openOcrImagePicker() {
    Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
    intent.addCategory(Intent.CATEGORY_OPENABLE);
    intent.setType("image/*");
    startActivityForResult(intent, REQUEST_PICK_OCR_IMAGE);
  }

  private void copyModelToPrivateStorage(Uri uri) {
    status.setText("Copying model into app storage...\nSource: " + displayName(uri));
    startLiteRt.setEnabled(false);
    copyExecutor.submit(() -> {
      File target = BackendConfig.selectedModelFile(this);
      File temp = new File(target.getParentFile(), target.getName() + ".tmp");
      try {
        File parent = target.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
          throw new IllegalStateException("Cannot create model directory: " + parent.getAbsolutePath());
        }
        long copied = copyUriToFile(uri, temp);
        if (target.exists() && !target.delete()) {
          throw new IllegalStateException("Cannot replace existing model file");
        }
        if (!temp.renameTo(target)) {
          throw new IllegalStateException("Cannot move model into final path");
        }
        runOnUiThread(() -> {
          status.setText("Model copied: " + humanBytes(copied));
          refreshModelStatus();
        });
      } catch (Exception e) {
        if (temp.exists()) temp.delete();
        runOnUiThread(() -> {
          status.setText("Model copy failed: " + e.getMessage());
          refreshModelStatus();
        });
      }
    });
  }

  private void setOcrMode(String mode) {
    selectedOcrMode = OCR_MODE_FULL.equals(mode) ? OCR_MODE_FULL : OCR_MODE_FAST;
    saveOcrPreferences();
    refreshOcrModeStatus();
  }

  private void refreshOcrModeStatus() {
    boolean full = OCR_MODE_FULL.equals(selectedOcrMode);
    if (full) {
      ocrModeStatus.setText("OCR mode: full (original image, 768 tokens)");
    } else {
      ocrModeStatus.setText("OCR mode: fast (1280px JPEG 85, 256 tokens)");
    }
    if (fastModeButton != null) fastModeButton.setEnabled(!ocrRunning && full);
    if (fullModeButton != null) fullModeButton.setEnabled(!ocrRunning && !full);
  }

  private void runSelectedOcr() {
    if (ocrRunning) return;
    Uri image = selectedOcrImage;
    if (image == null) {
      ocrResult.setText("Select an OCR image first.");
      return;
    }
    String backendUrl = normalizedBackendUrl();
    String imageName = displayName(image);
    String modeName = selectedOcrMode;
    saveOcrPreferences();
    OcrMode mode = OcrMode.from(modeName);
    setOcrRunning(true);
    ocrResult.setText("Preparing " + modeName + " OCR..." + "\nBackend: " + backendUrl);
    copyExecutor.submit(() -> {
      try {
        OcrUpload upload = prepareOcrUpload(image, mode);
        runOnUiThread(() -> ocrResult.setText(
            "Running " + modeName + " OCR..."
                + "\nBackend: " + backendUrl
                + "\nUpload: " + humanBytes(upload.bytes.length)));
        String raw = postGenerate(backendUrl, mode, upload);
        String text = emptyFallback(JsonUtil.stringValue(raw, "response"), "");
        int inferenceMs = JsonUtil.intValue(raw, "inference_ms", -1);
        int totalMs = JsonUtil.intValue(raw, "total_ms", -1);
        int imageBytes = JsonUtil.intValue(raw, "image_bytes", upload.bytes.length);
        String engine = emptyFallback(JsonUtil.stringValue(raw, "engine"), "unknown");
        OcrHistoryEntry entry = new OcrHistoryEntry(
            System.currentTimeMillis(),
            modeName,
            backendUrl,
            engine,
            imageName,
            imageBytes,
            inferenceMs,
            totalMs,
            text);
        saveOcrHistoryEntry(entry);
        runOnUiThread(() -> {
          showOcrEntryResult(entry);
          refreshDiagnosticsStatus();
          refreshHistoryList();
          setOcrRunning(false);
        });
      } catch (Exception e) {
        runOnUiThread(() -> {
          ocrResult.setText(userMessageForOcrError(e, backendUrl));
          setOcrRunning(false);
        });
      }
    });
  }

  private OcrUpload prepareOcrUpload(Uri uri, OcrMode mode) throws Exception {
    byte[] original = readUriBytes(uri);
    String name = displayName(uri);
    String contentType = getContentResolver().getType(uri);
    if (contentType == null || contentType.isEmpty()) contentType = "application/octet-stream";
    if (!mode.compressImage) {
      return new OcrUpload(name, contentType, original);
    }
    Bitmap bitmap = BitmapFactory.decodeByteArray(original, 0, original.length);
    if (bitmap == null) throw new IllegalArgumentException("Selected image cannot be decoded");
    int width = bitmap.getWidth();
    int height = bitmap.getHeight();
    int longest = Math.max(width, height);
    Bitmap outputBitmap = bitmap;
    if (longest > 1280) {
      float scale = 1280f / longest;
      int scaledWidth = Math.max(1, Math.round(width * scale));
      int scaledHeight = Math.max(1, Math.round(height * scale));
      outputBitmap = Bitmap.createScaledBitmap(bitmap, scaledWidth, scaledHeight, true);
    }
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    outputBitmap.compress(Bitmap.CompressFormat.JPEG, 85, out);
    if (outputBitmap != bitmap) outputBitmap.recycle();
    bitmap.recycle();
    return new OcrUpload(jpegName(name), "image/jpeg", out.toByteArray());
  }

  private String postGenerate(String backendUrl, OcrMode mode, OcrUpload upload) throws Exception {
    String boundary = "----gemma-android-ui-" + UUID.randomUUID();
    byte[] body = multipartBody(boundary, mode, upload);
    HttpURLConnection connection = (HttpURLConnection) new URL(backendUrl + "/generate").openConnection();
    connection.setRequestMethod("POST");
    connection.setConnectTimeout(30_000);
    connection.setReadTimeout(300_000);
    connection.setDoOutput(true);
    connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
    connection.setRequestProperty("Content-Length", String.valueOf(body.length));
    try (OutputStream out = connection.getOutputStream()) {
      out.write(body);
    }
    int code = connection.getResponseCode();
    InputStream in = code >= 400 ? connection.getErrorStream() : connection.getInputStream();
    String raw = in == null ? "" : new String(readAll(in), StandardCharsets.UTF_8);
    connection.disconnect();
    if (code >= 400) throw new HttpStatusException(code, raw);
    return raw;
  }

  private byte[] multipartBody(String boundary, OcrMode mode, OcrUpload upload) throws Exception {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    writeField(out, boundary, "prompt", mode.prompt);
    writeField(out, boundary, "max_tokens", String.valueOf(mode.maxTokens));
    writeField(out, boundary, "temperature", "0.1");
    out.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
    out.write(("Content-Disposition: form-data; name=\"image\"; filename=\"" + multipartFileName(upload.fileName) + "\"\r\n").getBytes(StandardCharsets.UTF_8));
    out.write(("Content-Type: " + upload.contentType + "\r\n\r\n").getBytes(StandardCharsets.UTF_8));
    out.write(upload.bytes);
    out.write("\r\n".getBytes(StandardCharsets.UTF_8));
    out.write(("--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
    return out.toByteArray();
  }

  private static void writeField(ByteArrayOutputStream out, String boundary, String name, String value) throws Exception {
    out.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
    out.write(("Content-Disposition: form-data; name=\"" + name + "\"\r\n\r\n").getBytes(StandardCharsets.UTF_8));
    out.write(value.getBytes(StandardCharsets.UTF_8));
    out.write("\r\n".getBytes(StandardCharsets.UTF_8));
  }

  private String normalizedBackendUrl() {
    String value = backendUrlInput.getText().toString().trim();
    if (value.isEmpty()) value = DEFAULT_BACKEND_URL;
    while (value.endsWith("/")) value = value.substring(0, value.length() - 1);
    backendUrlInput.setText(value);
    return value;
  }

  private void saveOcrPreferences() {
    if (backendUrlInput == null) return;
    getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        .edit()
        .putString(PREF_BACKEND_URL, normalizedBackendUrl())
        .putString(PREF_OCR_MODE, selectedOcrMode)
        .apply();
  }

  private void setOcrRunning(boolean running) {
    ocrRunning = running;
    if (backendUrlInput != null) backendUrlInput.setEnabled(!running);
    if (selectOcrImageButton != null) selectOcrImageButton.setEnabled(!running);
    if (runOcrButton != null) {
      runOcrButton.setEnabled(!running);
      runOcrButton.setText(running ? "OCR Running..." : "Run OCR");
    }
    if (copyOcrButton != null) copyOcrButton.setEnabled(!running && lastOcrText != null && !lastOcrText.isEmpty());
    refreshOcrModeStatus();
  }

  private void copyOcrText() {
    if (lastOcrText == null || lastOcrText.isEmpty()) {
      ocrResult.setText("No OCR text to copy.");
      return;
    }
    ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
    clipboard.setPrimaryClip(ClipData.newPlainText("OCR text", lastOcrText));
    status.setText("OCR text copied to clipboard.");
  }

  private void showOcrEntryResult(OcrHistoryEntry entry) {
    lastOcrText = entry.text;
    ocrResult.setText(
        "Engine: " + emptyFallback(entry.engine, "unknown")
            + "\nMode: " + emptyFallback(entry.mode, "unknown")
            + "\nImage: " + humanBytes(entry.imageBytes)
            + "\nInference: " + entry.inferenceMs + " ms"
            + "\nTotal: " + entry.totalMs + " ms"
            + "\nSource: " + emptyFallback(entry.imageName, "unknown")
            + "\nSaved: " + formatHistoryTime(entry.timestampMs)
            + "\n\n" + entry.text);
    setOcrRunning(false);
  }

  private void refreshHistoryList() {
    if (historyList == null || historyStatus == null) return;
    ArrayList<OcrHistoryEntry> entries = loadOcrHistory();
    historyList.removeAllViews();
    historyStatus.setText(entries.isEmpty() ? "History: none" : "History: " + entries.size() + " saved result(s)");
    for (OcrHistoryEntry entry : entries) {
      Button item = new Button(this);
      item.setAllCaps(false);
      item.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
      item.setText(historyLabel(entry));
      item.setOnClickListener(v -> {
        showOcrEntryResult(entry);
        status.setText("Loaded OCR history item.");
      });
      historyList.addView(item);
    }
  }

  private void clearOcrHistory() {
    File file = ocrHistoryFile();
    if (file.exists() && !file.delete()) {
      status.setText("Could not clear OCR history.");
      return;
    }
    lastOcrText = "";
    ocrResult.setText("OCR result: none");
    setOcrRunning(false);
    refreshHistoryList();
    status.setText("OCR history cleared.");
  }

  private void saveOcrHistoryEntry(OcrHistoryEntry entry) {
    ArrayList<OcrHistoryEntry> entries = loadOcrHistory();
    entries.add(0, entry);
    while (entries.size() > MAX_OCR_HISTORY) entries.remove(entries.size() - 1);
    saveOcrHistory(entries);
  }

  private ArrayList<OcrHistoryEntry> loadOcrHistory() {
    ArrayList<OcrHistoryEntry> entries = new ArrayList<>();
    File file = ocrHistoryFile();
    if (!file.exists()) return entries;
    try (InputStream in = new FileInputStream(file)) {
      String raw = new String(readAll(in), StandardCharsets.UTF_8);
      JSONArray array = new JSONArray(raw);
      for (int i = 0; i < array.length(); i++) {
        JSONObject item = array.optJSONObject(i);
        if (item == null) continue;
        entries.add(OcrHistoryEntry.fromJson(item));
      }
    } catch (Exception ignored) {
      // A corrupt history file should not block new OCR runs. The next successful run will rewrite it.
    }
    return entries;
  }

  private void saveOcrHistory(ArrayList<OcrHistoryEntry> entries) {
    JSONArray array = new JSONArray();
    for (OcrHistoryEntry entry : entries) {
      array.put(entry.toJson());
    }
    try (FileOutputStream out = new FileOutputStream(ocrHistoryFile())) {
      out.write(array.toString().getBytes(StandardCharsets.UTF_8));
    } catch (Exception e) {
      runOnUiThread(() -> status.setText("Could not save OCR history: " + e.getMessage()));
    }
  }

  private File ocrHistoryFile() {
    return new File(getFilesDir(), "ocr_history.json");
  }

  private static String historyLabel(OcrHistoryEntry entry) {
    return formatHistoryTime(entry.timestampMs)
        + " | " + emptyFallback(entry.mode, "?")
        + " | " + entry.totalMs + " ms"
        + " | " + emptyFallback(entry.imageName, "image")
        + "\n" + preview(entry.text);
  }

  private static String preview(String text) {
    String value = emptyFallback(text, "").replaceAll("\\s+", " ").trim();
    if (value.length() <= 120) return value;
    return value.substring(0, 120) + "...";
  }

  private static String formatHistoryTime(long timestampMs) {
    return new SimpleDateFormat("MM-dd HH:mm:ss", Locale.US).format(new Date(timestampMs));
  }

  private long copyUriToFile(Uri uri, File target) throws Exception {
    long copied = 0L;
    byte[] buffer = new byte[1024 * 1024];
    try (InputStream in = getContentResolver().openInputStream(uri);
         FileOutputStream out = new FileOutputStream(target)) {
      if (in == null) throw new IllegalStateException("Cannot open selected model file");
      int read;
      while ((read = in.read(buffer)) != -1) {
        out.write(buffer, 0, read);
        copied += read;
      }
    }
    return copied;
  }

  private byte[] readUriBytes(Uri uri) throws Exception {
    try (InputStream in = getContentResolver().openInputStream(uri)) {
      if (in == null) throw new IllegalStateException("Cannot open selected image");
      return readAll(in);
    }
  }

  private static byte[] readAll(InputStream in) throws Exception {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    byte[] buffer = new byte[1024 * 1024];
    int read;
    while ((read = in.read(buffer)) != -1) {
      out.write(buffer, 0, read);
    }
    return out.toByteArray();
  }

  private String displayName(Uri uri) {
    try (Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
      if (cursor != null && cursor.moveToFirst()) {
        int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
        if (index >= 0) return cursor.getString(index);
      }
    } catch (Exception ignored) {
      // URI display names are best-effort only.
    }
    return uri.toString();
  }

  private void refreshModelStatus() {
    File selected = BackendConfig.selectedModelFile(this);
    boolean exists = selected.exists();
    startLiteRt.setEnabled(exists);
    modelStatus.setText(
        "Selected model: " + (exists ? "ready (" + humanBytes(selected.length()) + ")" : "missing")
            + "\nApp model path: " + selected.getAbsolutePath()
            + "\nOld external path: " + BackendConfig.legacyExternalModelPath());
  }

  private void startServer(String engine) {
    String modelPath = BackendConfig.selectedModelPath(this);
    if (engine.toLowerCase(Locale.US).startsWith("litert") && !new File(modelPath).exists()) {
      status.setText("Select/Copy Model File before starting LiteRT server.");
      refreshModelStatus();
      return;
    }
    Intent intent = new Intent(this, ServerService.class);
    intent.setAction(ServerService.ACTION_START);
    intent.putExtra(ServerService.EXTRA_ENGINE, engine);
    intent.putExtra(ServerService.EXTRA_MODEL_PATH, modelPath);
    if (Build.VERSION.SDK_INT >= 26) {
      startForegroundService(intent);
    } else {
      startService(intent);
    }
    status.setText("Starting " + engine + " server on 127.0.0.1:8765\nModel: " + modelPath);
    refreshDiagnosticsStatus();
  }

  private void stopServer() {
    Intent intent = new Intent(this, ServerService.class);
    intent.setAction(ServerService.ACTION_STOP);
    startService(intent);
    status.setText("Gemma backend stopped");
    refreshModelStatus();
    refreshDiagnosticsStatus();
  }

  private void openOverlayPermissionSettings() {
    if (Build.VERSION.SDK_INT < 23) {
      status.setText("Overlay permission is not required on this Android version.");
      return;
    }
    Intent intent = new Intent(
        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
        Uri.parse("package:" + getPackageName()));
    startActivity(intent);
  }

  private void showFloatingAutomation() {
    if (Build.VERSION.SDK_INT >= 23 && !Settings.canDrawOverlays(this)) {
      status.setText("Enable overlay permission before showing floating controls.");
      openOverlayPermissionSettings();
      return;
    }
    startService(new Intent(this, FloatingAutomationService.class));
    AutomationLog.add("ui", "show floating automation controls");
    status.setText("Floating automation controls shown.");
  }

  private void hideFloatingAutomation() {
    stopService(new Intent(this, FloatingAutomationService.class));
    AutomationLog.add("ui", "hide floating automation controls");
    status.setText("Floating automation controls hidden.");
  }

  private void refreshAutomationLog() {
    if (automationLogView == null) return;
    String text = AutomationLog.text();
    automationLogView.setText(text.isEmpty() ? "No automation log yet." : text);
  }

  private void copyAutomationLog() {
    String text = AutomationLog.text();
    ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
    clipboard.setPrimaryClip(ClipData.newPlainText("Automation log", text));
    status.setText("Automation log copied to clipboard.");
  }

  private void refreshDiagnosticsStatus() {
    RequestDiagnostics.Snapshot latest = RequestDiagnostics.latest();
    if ("none".equals(latest.status)) {
      diagnosticsStatus.setText("Latest request: none");
      return;
    }
    if ("error".equals(latest.status)) {
      diagnosticsStatus.setText(
          "Latest request: error"
              + "\nEngine: " + emptyFallback(latest.engine, "unknown")
              + "\nError: " + emptyFallback(latest.errorMessage, "unknown"));
      return;
    }
    diagnosticsStatus.setText(
        "Latest request: success"
            + "\nEngine: " + latest.engine
            + "\nImage: " + latest.hasImage + " (" + RequestDiagnostics.humanBytes(latest.imageBytes) + ")"
            + "\nInference: " + latest.inferenceMs + " ms"
            + "\nTotal: " + latest.totalMs + " ms"
            + "\nResponse chars: " + latest.responseChars
            + "\nRequest: " + shortRequestId(latest.requestId));
  }

  private static String shortRequestId(String requestId) {
    if (requestId == null || requestId.length() <= 8) return emptyFallback(requestId, "unknown");
    return requestId.substring(0, 8);
  }

  private static String emptyFallback(String value, String fallback) {
    return value == null || value.isEmpty() ? fallback : value;
  }

  private static String multipartFileName(String name) {
    if (name == null || name.isEmpty()) return "upload.bin";
    return name.replace("\\", "_").replace("\"", "_").replace("\r", "_").replace("\n", "_");
  }

  private static String jpegName(String name) {
    if (name == null || name.isEmpty()) return "ocr.jpg";
    int dot = name.lastIndexOf('.');
    return (dot > 0 ? name.substring(0, dot) : name) + ".jpg";
  }

  private static String userMessageForOcrError(Exception e, String backendUrl) {
    if (e instanceof ConnectException) {
      return "OCR failed: cannot connect to backend."
          + "\nBackend: " + backendUrl
          + "\nStart the LiteRT GPU server, then run OCR again.";
    }
    if (e instanceof SocketTimeoutException) {
      return "OCR failed: backend timed out."
          + "\nBackend: " + backendUrl
          + "\nTry Fast OCR first, or restart the server if it is stuck.";
    }
    if (e instanceof HttpStatusException) {
      HttpStatusException http = (HttpStatusException) e;
      return "OCR failed: backend returned HTTP " + http.statusCode
          + "\n" + emptyFallback(http.body, "No error body returned.");
    }
    return "OCR failed: " + emptyFallback(e.getMessage(), e.getClass().getSimpleName());
  }

  private void requestNotificationPermission() {
    if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
      requestPermissions(new String[] {Manifest.permission.POST_NOTIFICATIONS}, 10);
    }
  }

  private static String humanBytes(long bytes) {
    if (bytes >= 1024L * 1024L * 1024L) return String.format(Locale.US, "%.2f GB", bytes / 1024.0 / 1024.0 / 1024.0);
    if (bytes >= 1024L * 1024L) return String.format(Locale.US, "%.2f MB", bytes / 1024.0 / 1024.0);
    if (bytes >= 1024L) return String.format(Locale.US, "%.2f KB", bytes / 1024.0);
    return bytes + " B";
  }

  private static final class OcrHistoryEntry {
    final long timestampMs;
    final String mode;
    final String backendUrl;
    final String engine;
    final String imageName;
    final int imageBytes;
    final int inferenceMs;
    final int totalMs;
    final String text;

    OcrHistoryEntry(
        long timestampMs,
        String mode,
        String backendUrl,
        String engine,
        String imageName,
        int imageBytes,
        int inferenceMs,
        int totalMs,
        String text) {
      this.timestampMs = timestampMs;
      this.mode = mode;
      this.backendUrl = backendUrl;
      this.engine = engine;
      this.imageName = imageName;
      this.imageBytes = imageBytes;
      this.inferenceMs = inferenceMs;
      this.totalMs = totalMs;
      this.text = text;
    }

    JSONObject toJson() {
      JSONObject object = new JSONObject();
      try {
        object.put("timestamp_ms", timestampMs);
        object.put("mode", mode);
        object.put("backend_url", backendUrl);
        object.put("engine", engine);
        object.put("image_name", imageName);
        object.put("image_bytes", imageBytes);
        object.put("inference_ms", inferenceMs);
        object.put("total_ms", totalMs);
        object.put("text", text);
      } catch (Exception ignored) {
        // JSONObject.put with simple scalar values should not fail in normal use.
      }
      return object;
    }

    static OcrHistoryEntry fromJson(JSONObject object) {
      return new OcrHistoryEntry(
          object.optLong("timestamp_ms", 0L),
          object.optString("mode", ""),
          object.optString("backend_url", ""),
          object.optString("engine", ""),
          object.optString("image_name", ""),
          object.optInt("image_bytes", -1),
          object.optInt("inference_ms", -1),
          object.optInt("total_ms", -1),
          object.optString("text", ""));
    }
  }

  private static final class HttpStatusException extends Exception {
    final int statusCode;
    final String body;

    HttpStatusException(int statusCode, String body) {
      super("HTTP " + statusCode);
      this.statusCode = statusCode;
      this.body = body;
    }
  }

  private static final class OcrMode {
    final String prompt;
    final int maxTokens;
    final boolean compressImage;

    OcrMode(String prompt, int maxTokens, boolean compressImage) {
      this.prompt = prompt;
      this.maxTokens = maxTokens;
      this.compressImage = compressImage;
    }

    static OcrMode from(String mode) {
      if (OCR_MODE_FULL.equals(mode)) {
        return new OcrMode(FULL_PROMPT, 768, false);
      }
      return new OcrMode(FAST_PROMPT, 256, true);
    }
  }

  private static final class OcrUpload {
    final String fileName;
    final String contentType;
    final byte[] bytes;

    OcrUpload(String fileName, String contentType, byte[] bytes) {
      this.fileName = fileName;
      this.contentType = contentType;
      this.bytes = bytes;
    }
  }
}
