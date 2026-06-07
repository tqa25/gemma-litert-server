package dev.gemma.androidbackend;

import android.app.Service;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.IBinder;
import android.provider.Settings;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class FloatingAutomationService extends Service {
  private static final String BASE_URL = "http://127.0.0.1:8765";

  private final ExecutorService executor = Executors.newSingleThreadExecutor();
  private WindowManager windowManager;
  private View bubble;
  private View menu;
  private View crosshair;

  @Override
  public int onStartCommand(Intent intent, int flags, int startId) {
    if (Build.VERSION.SDK_INT >= 23 && !Settings.canDrawOverlays(this)) {
      toast("Overlay permission is not enabled");
      stopSelf();
      return START_NOT_STICKY;
    }
    windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
    showBubble();
    return START_STICKY;
  }

  @Override
  public void onDestroy() {
    removeView(crosshair);
    removeView(menu);
    removeView(bubble);
    executor.shutdownNow();
    super.onDestroy();
  }

  @Override
  public IBinder onBind(Intent intent) {
    return null;
  }

  private void showBubble() {
    if (bubble != null) return;
    Button button = new Button(this);
    button.setText("G");
    button.setAllCaps(false);
    button.setOnClickListener(v -> toggleMenu());
    WindowManager.LayoutParams params = overlayParams(140, 140);
    params.x = 24;
    params.y = 240;
    makeDraggable(button, params);
    bubble = button;
    windowManager.addView(bubble, params);
  }

  private void toggleMenu() {
    if (menu == null) {
      showMenu();
    } else {
      removeView(menu);
      menu = null;
    }
  }

  private void showMenu() {
    LinearLayout layout = new LinearLayout(this);
    layout.setOrientation(LinearLayout.VERTICAL);
    layout.setPadding(12, 12, 12, 12);
    layout.setBackgroundColor(0xee222222);
    layout.addView(menuButton("Capture", v -> runHttp("capture", () -> {
      get("/automation/screenshot");
      get("/automation/screen-xml");
      return "captured";
    })));
    layout.addView(menuButton("Mark Article", v -> showCrosshair("chrome_discover_first_article")));
    layout.addView(menuButton("Mark Summary", v -> showCrosshair("gemini_summary_button")));
    layout.addView(menuButton("Mark Copy", v -> showCrosshair("gemini_copy_button")));
    layout.addView(menuButton("Run Once", v -> runHttp("run once", () -> post(
        "/automation/workflows/run",
        "{\"workflow\":\"chrome-discover-gemini-summary-once\",\"debug_capture\":true}"))));
    layout.addView(menuButton("Stop", v -> runHttp("stop", () -> post("/automation/stop", "{}"))));
    layout.addView(menuButton("Hide", v -> stopSelf()));
    WindowManager.LayoutParams params = overlayParams(420, WindowManager.LayoutParams.WRAP_CONTENT);
    params.x = 24;
    params.y = 390;
    menu = layout;
    windowManager.addView(menu, params);
  }

  private Button menuButton(String text, View.OnClickListener listener) {
    Button button = new Button(this);
    button.setText(text);
    button.setAllCaps(false);
    button.setOnClickListener(listener);
    return button;
  }

  private void showCrosshair(String key) {
    removeView(menu);
    menu = null;
    removeView(crosshair);
    LinearLayout layout = new LinearLayout(this);
    layout.setOrientation(LinearLayout.VERTICAL);
    layout.setPadding(10, 10, 10, 10);
    layout.setBackgroundColor(0xdd003344);
    TextView target = new TextView(this);
    target.setText("+\n" + key);
    target.setTextColor(0xffffffff);
    target.setGravity(Gravity.CENTER);
    target.setTextSize(20f);
    layout.addView(target);
    layout.addView(menuButton("Save", v -> saveCrosshair(key)));
    layout.addView(menuButton("Cancel", v -> {
      removeView(crosshair);
      crosshair = null;
    }));
    WindowManager.LayoutParams params = overlayParams(300, WindowManager.LayoutParams.WRAP_CONTENT);
    params.x = 390;
    params.y = 900;
    makeDraggable(layout, params);
    crosshair = layout;
    windowManager.addView(crosshair, params);
    toast("Drag crosshair to target, then Save");
  }

  private void saveCrosshair(String key) {
    if (crosshair == null) return;
    WindowManager.LayoutParams params = (WindowManager.LayoutParams) crosshair.getLayoutParams();
    int x = params.x + crosshair.getWidth() / 2;
    int y = params.y + 32;
    String body = "{\"key\":\"" + key + "\",\"x\":" + x + ",\"y\":" + y + "}";
    runHttp("calibrate", () -> post("/automation/calibrate", body));
    removeView(crosshair);
    crosshair = null;
  }

  private void makeDraggable(View view, WindowManager.LayoutParams params) {
    final int[] startX = new int[1];
    final int[] startY = new int[1];
    final float[] downX = new float[1];
    final float[] downY = new float[1];
    view.setOnTouchListener((v, event) -> {
      if (event.getAction() == MotionEvent.ACTION_DOWN) {
        startX[0] = params.x;
        startY[0] = params.y;
        downX[0] = event.getRawX();
        downY[0] = event.getRawY();
        return false;
      }
      if (event.getAction() == MotionEvent.ACTION_MOVE) {
        params.x = startX[0] + Math.round(event.getRawX() - downX[0]);
        params.y = startY[0] + Math.round(event.getRawY() - downY[0]);
        windowManager.updateViewLayout(view, params);
        return true;
      }
      return false;
    });
  }

  private WindowManager.LayoutParams overlayParams(int width, int height) {
    int type = Build.VERSION.SDK_INT >= 26
        ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        : WindowManager.LayoutParams.TYPE_PHONE;
    WindowManager.LayoutParams params = new WindowManager.LayoutParams(
        width,
        height,
        type,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
        PixelFormat.TRANSLUCENT);
    params.gravity = Gravity.TOP | Gravity.START;
    return params;
  }

  private void runHttp(String label, HttpAction action) {
    executor.submit(() -> {
      try {
        String response = action.run();
        toast(label + " ok");
        android.util.Log.i("GemmaAutomationOverlay", label + ": " + response);
      } catch (Exception e) {
        toast(label + " failed: " + e.getMessage());
      }
    });
  }

  private String get(String path) throws Exception {
    HttpURLConnection connection = (HttpURLConnection) new URL(BASE_URL + path).openConnection();
    connection.setConnectTimeout(5000);
    connection.setReadTimeout(300000);
    return readResponse(connection);
  }

  private String post(String path, String body) throws Exception {
    byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
    HttpURLConnection connection = (HttpURLConnection) new URL(BASE_URL + path).openConnection();
    connection.setRequestMethod("POST");
    connection.setDoOutput(true);
    connection.setConnectTimeout(5000);
    connection.setReadTimeout(300000);
    connection.setRequestProperty("Content-Type", "application/json");
    connection.setRequestProperty("Content-Length", String.valueOf(bytes.length));
    try (OutputStream out = connection.getOutputStream()) {
      out.write(bytes);
    }
    return readResponse(connection);
  }

  private String readResponse(HttpURLConnection connection) throws Exception {
    int code = connection.getResponseCode();
    InputStream in = code >= 400 ? connection.getErrorStream() : connection.getInputStream();
    String body = in == null ? "" : new String(readAll(in), StandardCharsets.UTF_8);
    connection.disconnect();
    if (code >= 400) throw new IllegalStateException("HTTP " + code + " " + body);
    return body;
  }

  private static byte[] readAll(InputStream in) throws Exception {
    java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
    byte[] buffer = new byte[8192];
    int read;
    while ((read = in.read(buffer)) != -1) {
      out.write(buffer, 0, read);
    }
    return out.toByteArray();
  }

  private void removeView(View view) {
    if (view == null || windowManager == null) return;
    try {
      windowManager.removeView(view);
    } catch (Exception ignored) {
    }
  }

  private void toast(String text) {
    new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> Toast.makeText(this, text, Toast.LENGTH_SHORT).show());
  }

  private interface HttpAction {
    String run() throws Exception;
  }
}
