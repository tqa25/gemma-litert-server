package dev.gemma.androidbackend;

final class GenerationRequest {
  final String prompt;
  final byte[] imageBytes;
  final int maxTokens;
  final double temperature;

  GenerationRequest(String prompt, byte[] imageBytes, int maxTokens, double temperature) {
    this.prompt = prompt;
    this.imageBytes = imageBytes;
    this.maxTokens = maxTokens;
    this.temperature = temperature;
  }

  boolean hasImage() {
    return imageBytes != null && imageBytes.length > 0;
  }
}
