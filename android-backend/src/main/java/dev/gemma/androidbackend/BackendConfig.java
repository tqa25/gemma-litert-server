package dev.gemma.androidbackend;

import android.content.Context;
import android.os.Environment;
import java.io.File;

final class BackendConfig {
  static final String HOST = "127.0.0.1";
  static final int PORT = 8765;
  static final String DEFAULT_ENGINE = "mock";
  static final int MAX_PROMPT_CHARS = 4000;
  static final int MAX_IMAGE_BYTES = 8 * 1024 * 1024;
  static final String MODEL_FILE_NAME = "gemma-4-E4B-it.litertlm";

  private BackendConfig() {}

  static File selectedModelFile(Context context) {
    return new File(new File(context.getFilesDir(), "models"), MODEL_FILE_NAME);
  }

  static String selectedModelPath(Context context) {
    return selectedModelFile(context).getAbsolutePath();
  }

  static String legacyExternalModelPath() {
    File external = Environment.getExternalStorageDirectory();
    return new File(external, "Models/" + MODEL_FILE_NAME).getAbsolutePath();
  }
}
