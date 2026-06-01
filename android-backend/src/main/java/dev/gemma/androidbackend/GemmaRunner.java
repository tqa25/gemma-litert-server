package dev.gemma.androidbackend;

interface GemmaRunner extends AutoCloseable {
  void initialize() throws Exception;

  String name();

  boolean isLoaded();

  String modelPath();

  long modelLoadMs();

  GenerationResult generate(GenerationRequest request) throws Exception;

  @Override
  void close() throws Exception;
}
