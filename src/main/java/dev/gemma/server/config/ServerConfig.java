package dev.gemma.server.config;

public record ServerConfig(
    String host,
    int port,
    String modelPath,
    String engine,
    int maxPromptChars,
    int maxImageMb,
    int defaultMaxTokens,
    long requestTimeoutMs) {

  public static ServerConfig fromEnv() {
    return new ServerConfig(
        env("GEMMA_HOST", "127.0.0.1"),
        envInt("GEMMA_PORT", 8765),
        env("GEMMA_MODEL_PATH", "models/gemma-4-E4B-it.litertlm"),
        env("GEMMA_ENGINE", "mock"),
        envInt("GEMMA_MAX_PROMPT_CHARS", 4000),
        envInt("GEMMA_MAX_IMAGE_MB", 8),
        envInt("GEMMA_DEFAULT_MAX_TOKENS", 512),
        envLong("GEMMA_REQUEST_TIMEOUT_MS", 120_000L));
  }

  private static String env(String name, String defaultValue) {
    String value = System.getenv(name);
    return value == null || value.isBlank() ? defaultValue : value;
  }

  private static int envInt(String name, int defaultValue) {
    String value = System.getenv(name);
    if (value == null || value.isBlank()) {
      return defaultValue;
    }
    return Integer.parseInt(value);
  }

  private static long envLong(String name, long defaultValue) {
    String value = System.getenv(name);
    if (value == null || value.isBlank()) {
      return defaultValue;
    }
    return Long.parseLong(value);
  }
}
