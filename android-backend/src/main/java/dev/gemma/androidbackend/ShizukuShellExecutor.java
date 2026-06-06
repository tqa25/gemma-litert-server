package dev.gemma.androidbackend;

import android.content.pm.PackageManager;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.util.concurrent.TimeUnit;
import rikka.shizuku.Shizuku;

final class ShizukuShellExecutor {
  private static final int REQUEST_CODE = 8765;

  boolean isBinderAvailable() {
    try {
      return Shizuku.pingBinder();
    } catch (Throwable ignored) {
      return false;
    }
  }

  boolean hasPermission() {
    try {
      return isBinderAvailable() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED;
    } catch (Throwable ignored) {
      return false;
    }
  }

  void requestPermissionIfPossible() {
    if (!isBinderAvailable()) return;
    if (hasPermission()) return;
    if (Shizuku.shouldShowRequestPermissionRationale()) return;
    Shizuku.requestPermission(REQUEST_CODE);
  }

  AutomationShellResult run(String command, long timeoutMs) throws Exception {
    if (!isBinderAvailable()) {
      throw new IllegalStateException("Shizuku binder is not available");
    }
    if (!hasPermission()) {
      requestPermissionIfPossible();
      throw new IllegalStateException("Shizuku permission is not granted");
    }
    long started = System.nanoTime();
    Process process = newProcess(command);
    ByteArrayOutputStream stdout = new ByteArrayOutputStream();
    ByteArrayOutputStream stderr = new ByteArrayOutputStream();
    Thread outThread = pump(process.getInputStream(), stdout);
    Thread errThread = pump(process.getErrorStream(), stderr);
    boolean finished = process.waitFor(timeoutMs, TimeUnit.MILLISECONDS);
    if (!finished) {
      process.destroyForcibly();
      throw new IllegalStateException("shell command timed out after " + timeoutMs + "ms");
    }
    outThread.join(1000);
    errThread.join(1000);
    long durationMs = (System.nanoTime() - started) / 1_000_000L;
    return new AutomationShellResult(
        process.exitValue(),
        stdout.toByteArray(),
        new String(stderr.toByteArray(), java.nio.charset.StandardCharsets.UTF_8),
        durationMs);
  }

  AutomationShellResult runChecked(String command, long timeoutMs) throws Exception {
    AutomationShellResult result = run(command, timeoutMs);
    if (result.exitCode != 0) {
      throw new IllegalStateException("shell command failed (" + result.exitCode + "): " + result.stderr);
    }
    return result;
  }

  private static Thread pump(InputStream input, ByteArrayOutputStream output) {
    Thread thread = new Thread(() -> {
      byte[] buffer = new byte[8192];
      try {
        int read;
        while ((read = input.read(buffer)) != -1) {
          output.write(buffer, 0, read);
        }
      } catch (Exception ignored) {
      }
    }, "automation-shell-pump");
    thread.start();
    return thread;
  }

  private static Process newProcess(String command) throws Exception {
    Method method = Shizuku.class.getDeclaredMethod("newProcess", String[].class, String[].class, String.class);
    method.setAccessible(true);
    return (Process) method.invoke(null, new String[] {"sh", "-c", command}, null, null);
  }
}
