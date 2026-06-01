package dev.gemma.server.inference;

public interface InferenceEngine extends AutoCloseable {
  void initialize() throws Exception;

  String name();

  boolean isLoaded();

  GenerationResult generate(GenerationRequest request) throws Exception;

  @Override
  void close() throws Exception;
}
