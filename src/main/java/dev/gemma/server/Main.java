package dev.gemma.server;

import dev.gemma.server.config.ServerConfig;
import dev.gemma.server.http.HttpServerApp;
import dev.gemma.server.inference.InferenceEngine;
import dev.gemma.server.inference.InferenceEngineFactory;

public final class Main {
  private Main() {}

  public static void main(String[] args) throws Exception {
    ServerConfig config = ServerConfig.fromEnv();
    long start = System.nanoTime();
    InferenceEngine engine = InferenceEngineFactory.create(config);
    engine.initialize();
    long modelLoadMs = (System.nanoTime() - start) / 1_000_000L;

    HttpServerApp app = new HttpServerApp(config, engine, modelLoadMs);
    app.start();
  }
}
