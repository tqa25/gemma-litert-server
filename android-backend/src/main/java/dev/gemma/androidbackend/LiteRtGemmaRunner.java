package dev.gemma.androidbackend;

import com.google.ai.edge.litertlm.Backend;
import com.google.ai.edge.litertlm.Content;
import com.google.ai.edge.litertlm.Contents;
import com.google.ai.edge.litertlm.Conversation;
import com.google.ai.edge.litertlm.ConversationConfig;
import com.google.ai.edge.litertlm.Engine;
import com.google.ai.edge.litertlm.EngineConfig;
import com.google.ai.edge.litertlm.Message;
import com.google.ai.edge.litertlm.SamplerConfig;

final class LiteRtGemmaRunner implements GemmaRunner {
  private final String modelPath;
  private final String cacheDir;
  private Engine engine;
  private long modelLoadMs;

  LiteRtGemmaRunner(String modelPath, String cacheDir) {
    this.modelPath = modelPath;
    this.cacheDir = cacheDir;
  }

  @Override
  public void initialize() {
    long start = System.nanoTime();
    EngineConfig config = new EngineConfig(
        modelPath,
        new Backend.CPU(),
        new Backend.CPU(),
        null,
        null,
        1,
        cacheDir
    );
    engine = new Engine(config);
    engine.initialize();
    modelLoadMs = (System.nanoTime() - start) / 1_000_000L;
  }

  @Override
  public String name() { return "litert-android"; }

  @Override
  public boolean isLoaded() { return engine != null; }

  @Override
  public String modelPath() { return modelPath; }

  @Override
  public long modelLoadMs() { return modelLoadMs; }

  @Override
  public synchronized GenerationResult generate(GenerationRequest request) throws Exception {
    if (engine == null) throw new IllegalStateException("LiteRT engine is not initialized");
    long start = System.nanoTime();
    Contents contents = request.hasImage()
        ? Contents.Companion.of(new Content.ImageBytes(request.imageBytes), new Content.Text(request.prompt))
        : Contents.Companion.of(new Content.Text(request.prompt));
    ConversationConfig conversationConfig = new ConversationConfig(
        Contents.Companion.of("You are a concise OCR and image understanding backend."),
        java.util.Collections.emptyList(),
        java.util.Collections.emptyList(),
        new SamplerConfig(64, 0.95, request.temperature, 0)
    );
    String text;
    try (Conversation conversation = engine.createConversation(conversationConfig)) {
      Message message = conversation.sendMessage(contents, java.util.Collections.emptyMap());
      text = extractText(message);
    }
    long elapsed = (System.nanoTime() - start) / 1_000_000L;
    return new GenerationResult(text, elapsed, MockGemmaRunner.estimateTokens(text));
  }

  @Override
  public void close() {
    if (engine != null) {
      engine.close();
      engine = null;
    }
  }

  private static String extractText(Message message) {
    StringBuilder out = new StringBuilder();
    for (Content content : message.getContents().getContents()) {
      if (content instanceof Content.Text) {
        out.append(((Content.Text) content).getText());
      }
    }
    return out.length() == 0 ? message.toString() : out.toString();
  }
}
