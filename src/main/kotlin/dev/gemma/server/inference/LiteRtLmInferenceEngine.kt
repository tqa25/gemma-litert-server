package dev.gemma.server.inference

import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.LogSeverity
import com.google.ai.edge.litertlm.SamplerConfig

class LiteRtLmInferenceEngine(private val modelPath: String) : InferenceEngine {
    private var engine: Engine? = null

    override fun initialize() {
        Engine.setNativeMinLogSeverity(LogSeverity.ERROR)
        val config = EngineConfig(
            modelPath = modelPath,
            backend = Backend.CPU(),
            visionBackend = Backend.CPU(),
        )
        engine = Engine(config).also { it.initialize() }
    }

    override fun name(): String = "litert"

    override fun isLoaded(): Boolean = engine != null

    override fun generate(request: GenerationRequest): GenerationResult {
        val activeEngine = engine ?: error("LiteRT-LM engine is not initialized")
        val started = System.nanoTime()
        val contents = if (request.hasImage()) {
            Contents.of(
                Content.ImageBytes(request.imageBytes()),
                Content.Text(request.prompt()),
            )
        } else {
            Contents.of(Content.Text(request.prompt()))
        }
        val conversationConfig = ConversationConfig(
            samplerConfig = SamplerConfig(
                topK = 64,
                topP = 0.95,
                temperature = request.temperature(),
            )
        )
        val response = activeEngine.createConversation(conversationConfig).use { conversation ->
            conversation.sendMessage(contents)
        }
        val text = response.contents.contents
            .filterIsInstance<Content.Text>()
            .joinToString("") { it.text }
            .ifBlank { response.toString() }
        val inferenceMs = (System.nanoTime() - started) / 1_000_000L
        return GenerationResult(text, inferenceMs, estimateTokens(text))
    }

    override fun close() {
        engine?.close()
        engine = null
    }

    private fun estimateTokens(text: String): Int =
        maxOf(1, Math.round(text.split(Regex("\\s+")).size * 1.35f))
}
