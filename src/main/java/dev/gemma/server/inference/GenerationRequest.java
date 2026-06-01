package dev.gemma.server.inference;

public record GenerationRequest(
    String prompt,
    byte[] imageBytes,
    int maxTokens,
    double temperature) {

  public boolean hasImage() {
    return imageBytes != null && imageBytes.length > 0;
  }
}
