# LiteRT-LM Integration Notes

The real LiteRT-LM JVM backend is wired in:

```text
src/main/kotlin/dev/gemma/server/inference/LiteRtLmInferenceEngine.kt
```

Dependency:

```kotlin
implementation("com.google.ai.edge.litertlm:litertlm-jvm:latest.release")
```

Resolved version during test:

```text
litertlm-jvm:0.12.0
```

Required runtime:

```text
JDK 21
Kotlin Gradle plugin 2.3.0
Gradle wrapper 8.10.2
```

Implementation shape:

```kotlin
val engineConfig = EngineConfig(
    modelPath = modelPath,
    backend = Backend.CPU(),
    visionBackend = Backend.CPU(),
)
val engine = Engine(engineConfig)
engine.initialize()

val contents = Contents.of(
    Content.ImageBytes(imageBytes),
    Content.Text(prompt),
)
val response = engine.createConversation(conversationConfig).use { conversation ->
    conversation.sendMessage(contents)
}
```

Notes:

- CPU backend works on Oracle VM arm64.
- `SamplerConfig` exposes `topK`, `topP`, `temperature`, and `seed`; it does not expose `maxTokens` in 0.12.0.
- The current server still accepts `max_tokens` for API stability, but LiteRT-LM 0.12.0 does not enforce it through `SamplerConfig`. Keep prompts concise until streaming/cancel timeout is added.
- Response text is extracted from `Message.contents.contents` by collecting `Content.Text`.

References:

- https://github.com/google-ai-edge/LiteRT-LM/blob/main/docs/api/kotlin/getting_started.md
- https://huggingface.co/litert-community/gemma-4-E4B-it-litert-lm
