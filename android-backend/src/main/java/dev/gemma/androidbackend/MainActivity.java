package dev.gemma.androidbackend;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.OpenableColumns;
import android.database.Cursor;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class MainActivity extends Activity {
  private static final int REQUEST_PICK_MODEL = 2001;
  private final ExecutorService copyExecutor = Executors.newSingleThreadExecutor();
  private final Handler diagnosticsHandler = new Handler(Looper.getMainLooper());
  private final Runnable diagnosticsRefresh = new Runnable() {
    @Override
    public void run() {
      refreshDiagnosticsStatus();
      diagnosticsHandler.postDelayed(this, 1000L);
    }
  };
  private TextView status;
  private TextView modelStatus;
  private TextView diagnosticsStatus;
  private Button startLiteRt;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    requestNotificationPermission();

    LinearLayout layout = new LinearLayout(this);
    layout.setOrientation(LinearLayout.VERTICAL);
    layout.setPadding(32, 32, 32, 32);
    layout.setGravity(Gravity.CENTER_HORIZONTAL);

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

    setContentView(layout);
    refreshModelStatus();
    refreshDiagnosticsStatus();
  }

  @Override
  protected void onResume() {
    super.onResume();
    diagnosticsHandler.post(diagnosticsRefresh);
  }

  @Override
  protected void onPause() {
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
    }
  }

  private void openModelPicker() {
    Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
    intent.addCategory(Intent.CATEGORY_OPENABLE);
    intent.setType("*/*");
    startActivityForResult(intent, REQUEST_PICK_MODEL);
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
    if ("litert".equalsIgnoreCase(engine) && !new File(modelPath).exists()) {
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
}
