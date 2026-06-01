package dev.gemma.androidbackend;

import android.os.Environment;
import java.io.File;

final class BackendConfig {
  static final String HOST = "127.0.0.1";
  static final int PORT = 8765;
  static final String DEFAULT_ENGINE = "mock";
  static final int MAX_PROMPT_CHARS = 4000;
  static final int MAX_IMAGE_BYTES = 8 * 1024 * 1024;

  private BackendConfig() {}

  static String defaultModelPath() {
    File external = Environment.getExternalStorageDirectory();
    return new File(external, "Models/gemma-4-E4B-it.litertlm").getAbsolutePath();
  }
}
