package dev.gemma.androidbackend;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class ServerService extends Service {
  public static final String ACTION_START = "dev.gemma.androidbackend.START";
  public static final String ACTION_STOP = "dev.gemma.androidbackend.STOP";
  public static final String EXTRA_ENGINE = "engine";
  private static final String CHANNEL_ID = "gemma-backend";

  private final ExecutorService executor = Executors.newSingleThreadExecutor();
  private GemmaRunner runner;
  private HttpApiServer server;

  @Override
  public void onCreate() {
    super.onCreate();
    ensureChannel();
  }

  @Override
  public int onStartCommand(Intent intent, int flags, int startId) {
    String action = intent == null ? ACTION_START : intent.getAction();
    if (ACTION_STOP.equals(action)) {
      stopSelf();
      return START_NOT_STICKY;
    }
    startForeground(1001, notification("Starting Gemma backend..."));
    String engine = intent == null ? BackendConfig.DEFAULT_ENGINE : intent.getStringExtra(EXTRA_ENGINE);
    if (engine == null || engine.isEmpty()) engine = BackendConfig.DEFAULT_ENGINE;
    final String selectedEngine = engine;
    executor.submit(() -> startServer(selectedEngine));
    return START_STICKY;
  }

  private void startServer(String engineName) {
    try {
      if (server != null) return;
      String modelPath = BackendConfig.defaultModelPath();
      if ("litert".equalsIgnoreCase(engineName)) {
        runner = new LiteRtGemmaRunner(modelPath, getCacheDir().getAbsolutePath());
      } else {
        runner = new MockGemmaRunner(modelPath);
      }
      runner.initialize();
      server = new HttpApiServer(this, runner);
      server.start();
      NotificationManager manager = getSystemService(NotificationManager.class);
      manager.notify(1001, notification("Gemma backend running on 127.0.0.1:8765 (" + runner.name() + ")"));
    } catch (Exception e) {
      android.util.Log.e("GemmaBackend", "failed to start server", e);
      NotificationManager manager = getSystemService(NotificationManager.class);
      manager.notify(1001, notification("Gemma backend failed: " + e.getMessage()));
    }
  }

  @Override
  public void onDestroy() {
    if (server != null) {
      server.stop();
      server = null;
    }
    try {
      if (runner != null) runner.close();
    } catch (Exception e) {
      android.util.Log.e("GemmaBackend", "failed to close runner", e);
    }
    executor.shutdownNow();
    super.onDestroy();
  }

  @Override
  public IBinder onBind(Intent intent) {
    return null;
  }

  private void ensureChannel() {
    if (Build.VERSION.SDK_INT >= 26) {
      NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "Gemma Backend", NotificationManager.IMPORTANCE_LOW);
      NotificationManager manager = getSystemService(NotificationManager.class);
      manager.createNotificationChannel(channel);
    }
  }

  private Notification notification(String text) {
    Notification.Builder builder = Build.VERSION.SDK_INT >= 26
        ? new Notification.Builder(this, CHANNEL_ID)
        : new Notification.Builder(this);
    return builder
        .setContentTitle("Gemma Backend")
        .setContentText(text)
        .setSmallIcon(android.R.drawable.stat_sys_upload_done)
        .setOngoing(true)
        .build();
  }
}
