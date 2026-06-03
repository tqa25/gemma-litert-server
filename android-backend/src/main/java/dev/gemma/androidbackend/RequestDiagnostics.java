package dev.gemma.androidbackend;

import java.util.Locale;

final class RequestDiagnostics {
  static final Snapshot EMPTY = new Snapshot(
      "none",
      "No requests yet",
      "",
      false,
      0L,
      0L,
      0L,
      0,
      "",
      0L);

  private static Snapshot latest = EMPTY;

  private RequestDiagnostics() {}

  static synchronized void recordSuccess(
      String requestId,
      String engine,
      boolean hasImage,
      long imageBytes,
      long inferenceMs,
      long totalMs,
      int responseChars) {
    latest = new Snapshot(
        "success",
        requestId,
        engine,
        hasImage,
        imageBytes,
        inferenceMs,
        totalMs,
        responseChars,
        "",
        System.currentTimeMillis());
  }

  static synchronized void recordError(String engine, String message) {
    latest = new Snapshot(
        "error",
        "",
        engine == null ? "" : engine,
        false,
        0L,
        0L,
        0L,
        0,
        message == null ? "unknown error" : message,
        System.currentTimeMillis());
  }

  static synchronized void clear() {
    latest = EMPTY;
  }

  static synchronized Snapshot latest() {
    return latest;
  }

  static String humanBytes(long bytes) {
    if (bytes >= 1024L * 1024L * 1024L) return String.format(Locale.US, "%.2f GB", bytes / 1024.0 / 1024.0 / 1024.0);
    if (bytes >= 1024L * 1024L) return String.format(Locale.US, "%.2f MB", bytes / 1024.0 / 1024.0);
    if (bytes >= 1024L) return String.format(Locale.US, "%.2f KB", bytes / 1024.0);
    return bytes + " B";
  }

  static final class Snapshot {
    final String status;
    final String requestId;
    final String engine;
    final boolean hasImage;
    final long imageBytes;
    final long inferenceMs;
    final long totalMs;
    final int responseChars;
    final String errorMessage;
    final long updatedAtMs;

    Snapshot(
        String status,
        String requestId,
        String engine,
        boolean hasImage,
        long imageBytes,
        long inferenceMs,
        long totalMs,
        int responseChars,
        String errorMessage,
        long updatedAtMs) {
      this.status = status;
      this.requestId = requestId;
      this.engine = engine;
      this.hasImage = hasImage;
      this.imageBytes = imageBytes;
      this.inferenceMs = inferenceMs;
      this.totalMs = totalMs;
      this.responseChars = responseChars;
      this.errorMessage = errorMessage;
      this.updatedAtMs = updatedAtMs;
    }
  }
}
