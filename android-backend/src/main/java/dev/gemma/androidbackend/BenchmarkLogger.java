package dev.gemma.androidbackend;

import android.content.Context;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Map;

final class BenchmarkLogger {
  private final File logFile;

  BenchmarkLogger(Context context) {
    logFile = new File(context.getExternalFilesDir(null), "benchmark.jsonl");
  }

  synchronized void append(Map<String, ?> row) {
    try (FileWriter writer = new FileWriter(logFile, true)) {
      writer.write(JsonUtil.object(row));
      writer.write("\n");
    } catch (IOException e) {
      android.util.Log.e("GemmaBackend", "benchmark log failed", e);
    }
  }
}
