package dev.gemma.server.inference;

public final class MockInferenceEngine implements InferenceEngine {
  private final String modelPath;
  private boolean loaded;

  public MockInferenceEngine(String modelPath) {
    this.modelPath = modelPath;
  }

  @Override
  public void initialize() {
    loaded = true;
  }

  @Override
  public String name() {
    return "mock";
  }

  @Override
  public boolean isLoaded() {
    return loaded;
  }

  @Override
  public GenerationResult generate(GenerationRequest request) {
    long start = System.nanoTime();
    String imageNote = request.hasImage() ? " image_bytes=" + request.imageBytes().length : " no_image";
    String response =
        "[mock] model_path="
            + modelPath
            + imageNote
            + " prompt_chars="
            + request.prompt().length()
            + " max_tokens="
            + request.maxTokens();
    long inferenceMs = (System.nanoTime() - start) / 1_000_000L;
    return new GenerationResult(response, inferenceMs, estimateTokens(response));
  }

  @Override
  public void close() {}

  private static int estimateTokens(String text) {
    return Math.max(1, Math.round(text.split("\\s+").length * 1.35f));
  }
}
