package dev.gemma.androidbackend;

import android.content.Context;
import fi.iki.elonen.NanoHTTPD;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

final class HttpApiServer extends NanoHTTPD {
  private final GemmaRunner runner;
  private final BenchmarkLogger logger;
  private final AutomationController automation;
  private final long startedAtMs;

  HttpApiServer(Context context, GemmaRunner runner) {
    super(BackendConfig.HOST, BackendConfig.PORT);
    this.runner = runner;
    this.logger = new BenchmarkLogger(context);
    this.automation = new AutomationController(context, runner);
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
      if (session.getUri().startsWith("/automation/")) {
        return handleAutomation(session);
      }
      return json(Response.Status.NOT_FOUND, JsonUtil.error("not_found", "Unknown route"));
    } catch (Exception e) {
      RequestDiagnostics.recordError(runner.name(), e.getMessage());
      return json(Response.Status.BAD_REQUEST, JsonUtil.error("bad_request", e.getMessage()));
    }
  }

  private Response handleAutomation(IHTTPSession session) throws Exception {
    String path = session.getUri();
    Method method = session.getMethod();
    String body = requestBody(session);
    if (Method.GET.equals(method) && "/automation/status".equals(path)) {
      return json(Response.Status.OK, automation.statusJson());
    }
    if (Method.GET.equals(method) && "/automation/config".equals(path)) {
      return json(Response.Status.OK, automation.configJson());
    }
    if (Method.GET.equals(method) && "/automation/logs".equals(path)) {
      LinkedHashMap<String, Object> result = new LinkedHashMap<>();
      result.put("ok", true);
      result.put("logs", AutomationLog.text());
      return json(Response.Status.OK, JsonUtil.object(result));
    }
    if (Method.GET.equals(method) && "/automation/current-app".equals(path)) {
      return json(Response.Status.OK, automation.currentAppJson());
    }
    if (Method.GET.equals(method) && "/automation/screenshot".equals(path)) {
      return json(Response.Status.OK, automation.screenshotJson());
    }
    if (Method.GET.equals(method) && "/automation/screen-xml".equals(path)) {
      return json(Response.Status.OK, automation.screenXmlJson());
    }
    if (Method.POST.equals(method) && "/automation/stop".equals(path)) {
      return json(Response.Status.OK, automation.stopJson());
    }
    if (Method.POST.equals(method) && "/automation/logs/clear".equals(path)) {
      AutomationLog.clear();
      return json(Response.Status.OK, "{\"ok\":true}");
    }
    if (Method.POST.equals(method) && "/automation/tap".equals(path)) {
      return json(Response.Status.OK, automation.tapJson(body));
    }
    if (Method.POST.equals(method) && "/automation/swipe".equals(path)) {
      return json(Response.Status.OK, automation.swipeJson(body));
    }
    if (Method.POST.equals(method) && "/automation/home".equals(path)) {
      return json(Response.Status.OK, automation.keyJson("home", "input keyevent KEYCODE_HOME"));
    }
    if (Method.POST.equals(method) && "/automation/back".equals(path)) {
      return json(Response.Status.OK, automation.keyJson("back", "input keyevent KEYCODE_BACK"));
    }
    if (Method.POST.equals(method) && "/automation/longpress-home".equals(path)) {
      return json(Response.Status.OK, automation.keyJson("longpress-home", "input keyevent --longpress KEYCODE_HOME"));
    }
    if (Method.POST.equals(method) && "/automation/wait".equals(path)) {
      return json(Response.Status.OK, automation.waitJson(body));
    }
    if (Method.POST.equals(method) && "/automation/open-app".equals(path)) {
      return json(Response.Status.OK, automation.openAppJson(body));
    }
    if (Method.POST.equals(method) && "/automation/calibrate".equals(path)) {
      return json(Response.Status.OK, automation.calibrateJson(body));
    }
    if (Method.POST.equals(method) && "/automation/workflows/run".equals(path)) {
      return json(Response.Status.OK, automation.runWorkflowJson(body));
    }
    if (Method.POST.equals(method) && "/automation/workflows/run-json".equals(path)) {
      return json(Response.Status.OK, automation.runJsonWorkflowJson(body));
    }
    return json(Response.Status.NOT_FOUND, JsonUtil.error("not_found", "Unknown automation route"));
  }

  private static String requestBody(IHTTPSession session) throws Exception {
    Map<String, String> files = new java.util.HashMap<>();
    session.parseBody(files);
    String body = files.get("postData");
    return body == null ? "" : body;
  }

  private String healthJson() {
    LinkedHashMap<String, Object> row = new LinkedHashMap<>();
    row.put("status", "ok");
    row.put("engine", runner.name());
    row.put("model_loaded", runner.isLoaded());
    row.put("model_path", runner.modelPath());
    row.put("model_load_ms", runner.modelLoadMs());
    row.put("uptime_ms", System.currentTimeMillis() - startedAtMs);
    row.put("last_request", diagnosticsJson());
    return JsonUtil.object(row);
  }

  private LinkedHashMap<String, Object> diagnosticsJson() {
    RequestDiagnostics.Snapshot latest = RequestDiagnostics.latest();
    LinkedHashMap<String, Object> row = new LinkedHashMap<>();
    row.put("status", latest.status);
    row.put("request_id", latest.requestId);
    row.put("engine", latest.engine);
    row.put("has_image", latest.hasImage);
    row.put("image_bytes", latest.imageBytes);
    row.put("inference_ms", latest.inferenceMs);
    row.put("total_ms", latest.totalMs);
    row.put("response_chars", latest.responseChars);
    row.put("error", latest.errorMessage);
    row.put("updated_at_ms", latest.updatedAtMs);
    return row;
  }

  private Response handleGenerate(IHTTPSession session) throws Exception {
    String requestId = UUID.randomUUID().toString();
    long started = System.nanoTime();
    Map<String, String> files = new java.util.HashMap<>();
    session.parseBody(files);
    Map<String, String> params = session.getParms();
    String body = files.get("postData");
    if (body == null) body = "";

    String prompt = firstNonEmpty(params.get("prompt"), JsonUtil.stringValue(body, "prompt"));
    if (prompt == null || prompt.trim().isEmpty()) throw new IllegalArgumentException("Missing required field: prompt");
    if (prompt.length() > BackendConfig.MAX_PROMPT_CHARS) throw new IllegalArgumentException("prompt too long");

    byte[] imageBytes = imageBytesFromMultipart(files);
    if (imageBytes == null) imageBytes = imageBytesFromJson(body);
    if (imageBytes != null && imageBytes.length > BackendConfig.MAX_IMAGE_BYTES) throw new IllegalArgumentException("image too large");

    GenerationRequest request = new GenerationRequest(
        prompt,
        imageBytes,
        intParam(params, body, "max_tokens", 256),
        doubleParam(params, body, "temperature", 0.2d));
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
    RequestDiagnostics.recordSuccess(
        requestId,
        runner.name(),
        request.hasImage(),
        imageBytes == null ? 0 : imageBytes.length,
        result.inferenceMs,
        totalMs,
        result.response.length());

    LinkedHashMap<String, Object> timing = new LinkedHashMap<>();
    timing.put("inference_ms", result.inferenceMs);
    timing.put("total_ms", totalMs);
    LinkedHashMap<String, Object> meta = new LinkedHashMap<>();
    meta.put("engine", runner.name());
    meta.put("has_image", request.hasImage());
    meta.put("prompt_chars", prompt.length());
    meta.put("image_bytes", imageBytes == null ? 0 : imageBytes.length);
    meta.put("response_chars", result.response.length());
    meta.put("output_tokens_estimate", result.outputTokensEstimate);
    LinkedHashMap<String, Object> response = new LinkedHashMap<>();
    response.put("request_id", requestId);
    response.put("response", result.response);
    response.put("timing", timing);
    response.put("meta", meta);
    return json(Response.Status.OK, JsonUtil.object(response));
  }

  private static byte[] imageBytesFromMultipart(Map<String, String> files) throws Exception {
    String path = firstNonEmpty(files.get("image"), files.get("image_file"), files.get("file"));
    if (path == null) return null;
    return Files.readAllBytes(Paths.get(path));
  }

  private static byte[] imageBytesFromJson(String body) {
    String imageBase64 = JsonUtil.stringValue(body, "image_base64");
    if (imageBase64 == null || imageBase64.isEmpty()) return null;
    return Base64.getDecoder().decode(imageBase64.getBytes(StandardCharsets.UTF_8));
  }

  private static int intParam(Map<String, String> params, String body, String key, int defaultValue) {
    String value = firstNonEmpty(params.get(key), JsonUtil.stringValue(body, key));
    if (value == null) return JsonUtil.intValue(body, key, defaultValue);
    return Integer.parseInt(value);
  }

  private static double doubleParam(Map<String, String> params, String body, String key, double defaultValue) {
    String value = firstNonEmpty(params.get(key), JsonUtil.stringValue(body, key));
    if (value == null) return JsonUtil.doubleValue(body, key, defaultValue);
    return Double.parseDouble(value);
  }

  private static String firstNonEmpty(String... values) {
    for (String value : values) {
      if (value != null && !value.isEmpty()) return value;
    }
    return null;
  }

  private static Response json(Response.Status status, String body) {
    return newFixedLengthResponse(status, "application/json; charset=utf-8", body);
  }
}
