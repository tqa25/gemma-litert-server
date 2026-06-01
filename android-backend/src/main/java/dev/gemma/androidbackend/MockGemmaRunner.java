package dev.gemma.androidbackend;

final class MockGemmaRunner implements GemmaRunner {
  private final String modelPath;
  private boolean loaded;
  private long modelLoadMs;

  MockGemmaRunner(String modelPath) {
    this.modelPath = modelPath;
  }

  @Override
  public void initialize() {
    long start = System.nanoTime();
    loaded = true;
    modelLoadMs = (System.nanoTime() - start) / 1_000_000L;
  }

  @Override
  public String name() { return "mock-android"; }

  @Override
  public boolean isLoaded() { return loaded; }

  @Override
  public String modelPath() { return modelPath; }

  @Override
  public long modelLoadMs() { return modelLoadMs; }

  @Override
  public GenerationResult generate(GenerationRequest request) {
    long start = System.nanoTime();
    String imageNote = request.hasImage() ? " image_bytes=" + request.imageBytes.length : " no_image";
    String response = "[mock-android] model_path=" + modelPath + imageNote
        + " prompt_chars=" + request.prompt.length() + " max_tokens=" + request.maxTokens;
    long elapsed = (System.nanoTime() - start) / 1_000_000L;
    return new GenerationResult(response, elapsed, estimateTokens(response));
  }

  @Override
  public void close() {}

  static int estimateTokens(String text) {
    String trimmed = text == null ? "" : text.trim();
    if (trimmed.isEmpty()) return 1;
    return Math.max(1, Math.round(trimmed.split("\\s+").length * 1.35f));
  }
}
