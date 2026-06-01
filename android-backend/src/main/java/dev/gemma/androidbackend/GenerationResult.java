package dev.gemma.androidbackend;

final class GenerationResult {
  final String response;
  final long inferenceMs;
  final int outputTokensEstimate;

  GenerationResult(String response, long inferenceMs, int outputTokensEstimate) {
    this.response = response;
    this.inferenceMs = inferenceMs;
    this.outputTokensEstimate = outputTokensEstimate;
  }
}
