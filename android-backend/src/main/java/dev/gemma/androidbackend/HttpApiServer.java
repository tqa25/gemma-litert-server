package dev.gemma.androidbackend;

import android.content.Context;
import fi.iki.elonen.NanoHTTPD;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

final class HttpApiServer extends NanoHTTPD {
  private final GemmaRunner runner;
  private final BenchmarkLogger logger;
  private final long startedAtMs;

  HttpApiServer(Context context, GemmaRunner runner) {
    super(BackendConfig.HOST, BackendConfig.PORT);
    this.runner = runner;
    this.logger = new BenchmarkLogger(context);
    this.startedAtMs = System.currentTimeMillis();
  }

  @Override
  public Response serve(IHTTPSession session) {
    try {
      if (Method.GET.equals(session.getMethod()) && "/health".equals(session.getUri())) {
        return json(Response.Status.OK, healthJson());
      }
      if (Method.POST.equals(session.getMethod()) && "/generate".equals(session.getUri())) {
        return handleGenerate(session);
      }
      return json(Response.Status.NOT_FOUND, JsonUtil.error("not_found", "Unknown route"));
    } catch (Exception e) {
      return json(Response.Status.BAD_REQUEST, JsonUtil.error("bad_request", e.getMessage()));
    }
  }

  private String healthJson() {
    LinkedHashMap<String, Object> row = new LinkedHashMap<>();
    row.put("status", "ok");
    row.put("engine", runner.name());
    row.put("model_loaded", runner.isLoaded());
    row.put("model_path", runner.modelPath());
    row.put("model_load_ms", runner.modelLoadMs());
    row.put("uptime_ms", System.currentTimeMillis() - startedAtMs);
    return JsonUtil.object(row);
  }

  private Response handleGenerate(IHTTPSession session) throws Exception {
    String requestId = UUID.randomUUID().toString();
    long started = System.nanoTime();
    Map<String, String> files = new java.util.HashMap<>();
    session.parseBody(files);
    String body = files.get("postData");
    if (body == null) body = "";

    String prompt = JsonUtil.stringValue(body, "prompt");
    if (prompt == null || prompt.trim().isEmpty()) throw new IllegalArgumentException("Missing required field: prompt");
    if (prompt.length() > BackendConfig.MAX_PROMPT_CHARS) throw new IllegalArgumentException("prompt too long");

    String imageBase64 = JsonUtil.stringValue(body, "image_base64");
    byte[] imageBytes = null;
    if (imageBase64 != null && !imageBase64.isEmpty()) {
      imageBytes = Base64.getDecoder().decode(imageBase64.getBytes(StandardCharsets.UTF_8));
      if (imageBytes.length > BackendConfig.MAX_IMAGE_BYTES) throw new IllegalArgumentException("image too large");
    }

    GenerationRequest request = new GenerationRequest(
        prompt,
        imageBytes,
        JsonUtil.intValue(body, "max_tokens", 256),
        JsonUtil.doubleValue(body, "temperature", 0.2d));
    GenerationResult result = runner.generate(request);
    long totalMs = (System.nanoTime() - started) / 1_000_000L;

    LinkedHashMap<String, Object> log = new LinkedHashMap<>();
    log.put("timestamp", Instant.now().toString());
    log.put("request_id", requestId);
    log.put("engine", runner.name());
    log.put("has_image", request.hasImage());
    log.put("prompt_chars", prompt.length());
    log.put("image_bytes", imageBytes == null ? 0 : imageBytes.length);
    log.put("model_load_ms", runner.modelLoadMs());
    log.put("inference_ms", result.inferenceMs);
    log.put("total_ms", totalMs);
    log.put("response_chars", result.response.length());
    log.put("success", true);
    logger.append(log);

    LinkedHashMap<String, Object> timing = new LinkedHashMap<>();
    timing.put("inference_ms", result.inferenceMs);
    timing.put("total_ms", totalMs);
    LinkedHashMap<String, Object> meta = new LinkedHashMap<>();
    meta.put("engine", runner.name());
    meta.put("has_image", request.hasImage());
    meta.put("prompt_chars", prompt.length());
    meta.put("response_chars", result.response.length());
    meta.put("output_tokens_estimate", result.outputTokensEstimate);
    LinkedHashMap<String, Object> response = new LinkedHashMap<>();
    response.put("request_id", requestId);
    response.put("response", result.response);
    response.put("timing", timing);
    response.put("meta", meta);
    return json(Response.Status.OK, JsonUtil.object(response));
  }

  private static Response json(Response.Status status, String body) {
    return newFixedLengthResponse(status, "application/json; charset=utf-8", body);
  }
}
