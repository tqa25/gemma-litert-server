package dev.gemma.server.inference;

import dev.gemma.server.config.ServerConfig;

public final class InferenceEngineFactory {
  private InferenceEngineFactory() {}

  public static InferenceEngine create(ServerConfig config) {
    return switch (config.engine().toLowerCase()) {
      case "mock" -> new MockInferenceEngine(config.modelPath());
      case "litert" -> new LiteRtLmInferenceEngine(config.modelPath());
      default -> throw new IllegalArgumentException("Unknown GEMMA_ENGINE: " + config.engine());
    };
  }
}
