package dev.gemma.server.benchmark;

import dev.gemma.server.http.JsonUtil;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Map;

public final class BenchmarkLogger {
  private final Path path;

  public BenchmarkLogger(String path) {
    this.path = Path.of(path);
  }

  public synchronized void append(Map<String, ?> row) {
    try {
      Files.createDirectories(path.getParent());
      Files.writeString(
          path,
          JsonUtil.object(row) + "\n",
          StandardCharsets.UTF_8,
          StandardOpenOption.CREATE,
          StandardOpenOption.APPEND);
    } catch (IOException e) {
      System.err.println("Failed to write benchmark log: " + e.getMessage());
    }
  }
}
