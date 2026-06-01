package dev.gemma.server.http;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import dev.gemma.server.benchmark.BenchmarkLogger;
import dev.gemma.server.config.ServerConfig;
import dev.gemma.server.inference.GenerationRequest;
import dev.gemma.server.inference.GenerationResult;
import dev.gemma.server.inference.InferenceEngine;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;

public final class HttpServerApp {
  private final ServerConfig config;
  private final InferenceEngine engine;
  private final long modelLoadMs;
  private final long startedAtMs;
  private final BenchmarkLogger benchmarkLogger;

  public HttpServerApp(ServerConfig config, InferenceEngine engine, long modelLoadMs) {
    this.config = config;
    this.engine = engine;
    this.modelLoadMs = modelLoadMs;
    this.startedAtMs = System.currentTimeMillis();
    this.benchmarkLogger = new BenchmarkLogger("logs/benchmark.jsonl");
  }

  public void start() throws IOException {
    HttpServer server = HttpServer.create(new InetSocketAddress(config.host(), config.port()), 0);
    server.createContext("/health", this::health);
    server.createContext("/generate", this::generate);
    server.setExecutor(Executors.newSingleThreadExecutor());
    server.start();
    System.out.printf("gemma-litert-server listening on http://%s:%d%n", config.host(), config.port());
  }

  private void health(HttpExchange exchange) throws IOException {
    if (!"GET".equals(exchange.getRequestMethod())) {
      writeJson(exchange, 405, JsonUtil.error("method_not_allowed", "Use GET"));
      return;
    }
    String body =
        JsonUtil.object(
            Map.of(
                "status", "ok",
                "engine", engine.name(),
                "model_loaded", engine.isLoaded(),
                "model_path", config.modelPath(),
                "model_load_ms", modelLoadMs,
                "uptime_ms", System.currentTimeMillis() - startedAtMs));
    writeJson(exchange, 200, body);
  }

  private void generate(HttpExchange exchange) throws IOException {
    if (!"POST".equals(exchange.getRequestMethod())) {
      writeJson(exchange, 405, JsonUtil.error("method_not_allowed", "Use POST"));
      return;
    }

    String requestId = UUID.randomUUID().toString();
    long started = System.nanoTime();
    String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
    try {
      String prompt = JsonUtil.stringValue(body, "prompt");
      if (prompt == null || prompt.isBlank()) {
        throw new IllegalArgumentException("Missing required field: prompt");
      }
      if (prompt.length() > config.maxPromptChars()) {
        throw new IllegalArgumentException("prompt exceeds GEMMA_MAX_PROMPT_CHARS");
      }

      String imageBase64 = JsonUtil.stringValue(body, "image_base64");
      byte[] imageBytes = null;
      if (imageBase64 != null && !imageBase64.isBlank()) {
        imageBytes = Base64.getDecoder().decode(imageBase64);
        int maxBytes = config.maxImageMb() * 1024 * 1024;
        if (imageBytes.length > maxBytes) {
          throw new IllegalArgumentException("image_base64 exceeds GEMMA_MAX_IMAGE_MB");
        }
      }

      int maxTokens = JsonUtil.intValue(body, "max_tokens", config.defaultMaxTokens());
      double temperature = JsonUtil.doubleValue(body, "temperature", 0.2d);
      GenerationRequest request = new GenerationRequest(prompt, imageBytes, maxTokens, temperature);
      GenerationResult result = engine.generate(request);
      long totalMs = (System.nanoTime() - started) / 1_000_000L;

      Map<String, Object> benchmark = new LinkedHashMap<>();
      benchmark.put("timestamp", Instant.now().toString());
      benchmark.put("request_id", requestId);
      benchmark.put("engine", engine.name());
      benchmark.put("has_image", request.hasImage());
      benchmark.put("prompt_chars", prompt.length());
      benchmark.put("image_bytes", imageBytes == null ? 0 : imageBytes.length);
      benchmark.put("model_load_ms", modelLoadMs);
      benchmark.put("inference_ms", result.inferenceMs());
      benchmark.put("total_ms", totalMs);
      benchmark.put("response_chars", result.response().length());
      benchmark.put("success", true);
      benchmarkLogger.append(benchmark);

      String response =
          JsonUtil.object(
              Map.of(
                  "request_id", requestId,
                  "response", result.response(),
                  "timing",
                      Map.of(
                          "inference_ms", result.inferenceMs(),
                          "total_ms", totalMs),
                  "meta",
                      Map.of(
                          "model_path", config.modelPath(),
                          "engine", engine.name(),
                          "prompt_chars", prompt.length(),
                          "has_image", request.hasImage(),
                          "response_chars", result.response().length(),
                          "output_tokens_estimate", result.outputTokensEstimate())));
      writeJson(exchange, 200, response);
    } catch (Exception e) {
      long totalMs = (System.nanoTime() - started) / 1_000_000L;
      benchmarkLogger.append(
          Map.of(
              "timestamp", Instant.now().toString(),
              "request_id", requestId,
              "engine", engine.name(),
              "success", false,
              "total_ms", totalMs,
              "error", e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()));
      writeJson(exchange, 400, JsonUtil.error("bad_request", e.getMessage()));
    }
  }

  private static void writeJson(HttpExchange exchange, int status, String body) throws IOException {
    byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
    exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
    exchange.sendResponseHeaders(status, bytes.length);
    exchange.getResponseBody().write(bytes);
    exchange.close();
  }
}
