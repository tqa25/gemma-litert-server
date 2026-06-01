package dev.gemma.server.inference;

public record GenerationResult(
    String response,
    long inferenceMs,
    Integer outputTokensEstimate) {}
