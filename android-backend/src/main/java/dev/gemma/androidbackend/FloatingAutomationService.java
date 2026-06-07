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
  public static final String ACTION_SHOW_TAP = "dev.gemma.androidbackend.SHOW_TAP";
  public static final String EXTRA_X = "x";
  public static final String EXTRA_Y = "y";
  private static final String BASE_URL = "http://127.0.0.1:8765";

  private final ExecutorService executor = Executors.newSingleThreadExecutor();
  private WindowManager windowManager;
  private View bubble;
  private View menu;
  private View crosshair;
  private View crosshairControls;
  private View tapIndicator;

  @Override
  public int onStartCommand(Intent intent, int flags, int startId) {
    if (Build.VERSION.SDK_INT >= 23 && !Settings.canDrawOverlays(this)) {
      toast("Overlay permission is not enabled");
      stopSelf();
      return START_NOT_STICKY;
    }
    windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
    showBubble();
    if (intent != null && ACTION_SHOW_TAP.equals(intent.getAction())) {
      showTapIndicator(intent.getIntExtra(EXTRA_X, -1), intent.getIntExtra(EXTRA_Y, -1));
    }
    return START_STICKY;
  }

  @Override
  public void onDestroy() {
    removeView(crosshair);
    removeView(crosshairControls);
    removeView(tapIndicator);
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
    AutomationLog.add("overlay", "open menu");
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
    layout.addView(menuButton("Mark Menu", v -> showCrosshair("chrome_menu_button")));
    layout.addView(menuButton("Mark Reading", v -> showCrosshair("chrome_show_reading_mode")));
    layout.addView(menuButton("Mark Summary", v -> showCrosshair("gemini_summary_button")));
    layout.addView(menuButton("Mark Copy", v -> showCrosshair("gemini_copy_button")));
    layout.addView(menuButton("Run Reading", v -> runHttp("run reading", () -> post(
        "/automation/workflows/run",
        "{\"workflow\":\"chrome-discover-reading-gemma-summary-once\",\"debug_capture\":true}"))));
    layout.addView(menuButton("Run Gemini", v -> runHttp("run gemini", () -> post(
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
    AutomationLog.add("overlay", "mark " + key);
    removeView(menu);
    menu = null;
    removeView(crosshair);
    removeView(crosshairControls);
    TextView target = new TextView(this);
    target.setText("●");
    target.setTextColor(0xffffd400);
    target.setGravity(Gravity.CENTER);
    target.setTextSize(42f);
    target.setBackgroundColor(0x33000000);
    WindowManager.LayoutParams params = overlayParams(88, 88);
    params.x = 504;
    params.y = 900;
    makeDraggable(target, params);
    crosshair = target;
    windowManager.addView(crosshair, params);

    LinearLayout controls = new LinearLayout(this);
    controls.setOrientation(LinearLayout.VERTICAL);
    controls.setPadding(10, 10, 10, 10);
    controls.setBackgroundColor(0xee222222);
    TextView label = new TextView(this);
    label.setText(key);
    label.setTextColor(0xffffffff);
    controls.addView(label);
    controls.addView(menuButton("Save", v -> saveCrosshair(key)));
    controls.addView(menuButton("Cancel", v -> {
      removeView(crosshair);
      removeView(crosshairControls);
      crosshair = null;
      crosshairControls = null;
    }));
    WindowManager.LayoutParams controlParams = overlayParams(360, WindowManager.LayoutParams.WRAP_CONTENT);
    controlParams.x = 24;
    controlParams.y = 1450;
    crosshairControls = controls;
    windowManager.addView(crosshairControls, controlParams);
    toast("Drag crosshair to target, then Save");
  }

  private void showTapIndicator(int x, int y) {
    if (x < 0 || y < 0) return;
    removeView(tapIndicator);
    TextView dot = new TextView(this);
    dot.setText("●");
    dot.setTextColor(0xffffd400);
    dot.setGravity(Gravity.CENTER);
    dot.setTextSize(48f);
    dot.setBackgroundColor(0x00000000);
    WindowManager.LayoutParams params = overlayParams(96, 96);
    params.x = x - 48;
    params.y = y - 48;
    tapIndicator = dot;
    windowManager.addView(tapIndicator, params);
    new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
      removeView(tapIndicator);
      tapIndicator = null;
    }, 700L);
  }

  private void saveCrosshair(String key) {
    if (crosshair == null) return;
    WindowManager.LayoutParams params = (WindowManager.LayoutParams) crosshair.getLayoutParams();
    int x = params.x + crosshair.getWidth() / 2;
    int y = params.y + crosshair.getHeight() / 2;
    String body = "{\"key\":\"" + key + "\",\"x\":" + x + ",\"y\":" + y + "}";
    AutomationLog.add("overlay", "save " + key + " = " + x + "," + y);
    runHttp("calibrate", () -> post("/automation/calibrate", body));
    removeView(crosshair);
    removeView(crosshairControls);
    crosshair = null;
    crosshairControls = null;
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
    AutomationLog.add("overlay", "run " + label);
    executor.submit(() -> {
      try {
        String response = action.run();
        AutomationLog.add("overlay", label + " ok");
        toast(label + " ok");
        android.util.Log.i("GemmaAutomationOverlay", label + ": " + response);
      } catch (Exception e) {
        AutomationLog.add("overlay", label + " failed: " + e.getMessage());
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
