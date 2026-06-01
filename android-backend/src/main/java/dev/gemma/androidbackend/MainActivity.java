package dev.gemma.androidbackend;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

public final class MainActivity extends Activity {
  private TextView status;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    requestNotificationPermission();

    LinearLayout layout = new LinearLayout(this);
    layout.setOrientation(LinearLayout.VERTICAL);
    layout.setPadding(32, 32, 32, 32);
    layout.setGravity(Gravity.CENTER_HORIZONTAL);

    status = new TextView(this);
    status.setText("Gemma backend stopped\nModel: " + BackendConfig.defaultModelPath() + "\nPort: 127.0.0.1:8765");
    layout.addView(status);

    Button startMock = new Button(this);
    startMock.setText("Start Mock Server");
    startMock.setOnClickListener(v -> startServer("mock"));
    layout.addView(startMock);

    Button startLiteRt = new Button(this);
    startLiteRt.setText("Start LiteRT Server");
    startLiteRt.setOnClickListener(v -> startServer("litert"));
    layout.addView(startLiteRt);

    Button stop = new Button(this);
    stop.setText("Stop Server");
    stop.setOnClickListener(v -> stopServer());
    layout.addView(stop);

    setContentView(layout);
  }

  private void startServer(String engine) {
    Intent intent = new Intent(this, ServerService.class);
    intent.setAction(ServerService.ACTION_START);
    intent.putExtra(ServerService.EXTRA_ENGINE, engine);
    if (Build.VERSION.SDK_INT >= 26) {
      startForegroundService(intent);
    } else {
      startService(intent);
    }
    status.setText("Starting " + engine + " server on 127.0.0.1:8765\nModel: " + BackendConfig.defaultModelPath());
  }

  private void stopServer() {
    Intent intent = new Intent(this, ServerService.class);
    intent.setAction(ServerService.ACTION_STOP);
    startService(intent);
    status.setText("Gemma backend stopped");
  }

  private void requestNotificationPermission() {
    if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
      requestPermissions(new String[] {Manifest.permission.POST_NOTIFICATIONS}, 10);
    }
  }
}
