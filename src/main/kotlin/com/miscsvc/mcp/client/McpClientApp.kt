package com.miscsvc.mcp.client

import com.miscsvc.config.AppSettings
import com.miscsvc.json.JsonSupport
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

private const val MAX_TOOL_LOOPS = 8
private const val SYSTEM_PROMPT =
    "Sei un assistente che puo usare tool MCP per leggere/scrivere clienti e ordini. " +
        "Usa i tool quando servono dati reali dalle API. " +
        "Rispondi in italiano in modo chiaro e sintetico."

private val httpClient: HttpClient = HttpClient.newBuilder()
    .connectTimeout(Duration.ofSeconds(20))
    .build()

fun runMcpClient(settings: AppSettings, args: List<String>) {
    defaultMcpStdIOClient(settings).use { mcpClient ->
        mcpClient.start()

        if (args.isNotEmpty()) {
            val prompt = args.joinToString(" ").trim()
            val answer = askWithTools(prompt, mcpClient, settings)
            println(answer)
            return
        }

        println("Modalita interattiva. Scrivi 'exit' per uscire.")
        while (true) {
            print("Tu> ")
            val userInput = readlnOrNull()?.trim().orEmpty()
            if (userInput.lowercase() in setOf("exit", "quit")) {
                break
            }
            if (userInput.isBlank()) {
                continue
            }

            val answer = askWithTools(userInput, mcpClient, settings)
            println("LLM> $answer")
        }
    }
}

private fun askWithTools(
    userPrompt: String,
    mcpClient: McpStdIOClient,
    settings: AppSettings,
): String {
    val tools = mcpClient.listTools()
    val ollamaTools = tools.map(::toolToOllamaSchema)

    val messages = mutableListOf<MutableMap<String, Any?>>(
        mutableMapOf("role" to "system", "content" to SYSTEM_PROMPT),
        mutableMapOf("role" to "user", "content" to userPrompt),
    )

    repeat(MAX_TOOL_LOOPS) {
        val assistantMessage = ollamaChat(messages, ollamaTools, settings)
        messages.add(assistantMessage.toMutableMap())

        val toolCalls = assistantMessage["tool_calls"] as? List<*> ?: emptyList<Any?>()
        if (toolCalls.isEmpty()) {
            return assistantMessage["content"]?.toString() ?: ""
        }

        for (toolCall in toolCalls) {
            val toolCallMap = asStringAnyMap(toolCall)
            val function = asStringAnyMap(toolCallMap["function"])
            val toolName = function["name"]?.toString()?.takeIf { it.isNotBlank() } ?: continue
            val toolArgs = parseArguments(function["arguments"])

            val toolResult = mcpClient.callTool(toolName, toolArgs)
            val toolText = toolResultToText(toolResult)

            val toolMessage = mutableMapOf<String, Any?>(
                "role" to "tool",
                "name" to toolName,
                "content" to toolText,
            )

            val toolCallId = toolCallMap["id"]?.toString()
            if (!toolCallId.isNullOrBlank()) {
                toolMessage["tool_call_id"] = toolCallId
            }

            messages.add(toolMessage)
        }
    }

    return "Interrotto: superato il limite massimo di tool call iterative."
}

private fun ollamaChat(
    messages: List<Map<String, Any?>>,
    tools: List<Map<String, Any?>>,
    settings: AppSettings,
): Map<String, Any?> {
    val payload = mapOf(
        "model" to settings.ollamaModel,
        "messages" to messages,
        "tools" to tools,
        "stream" to false,
    )

    val url = settings.ollamaUrl.trimEnd('/') + "/api/chat"
    val request = HttpRequest.newBuilder()
        .uri(URI.create(url))
        .timeout(Duration.ofSeconds(90))
        .header("Content-Type", "application/json")
        .POST(HttpRequest.BodyPublishers.ofString(JsonSupport.mapper.writeValueAsString(payload)))
        .build()

    val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
    if (response.statusCode() >= 400) {
        throw IllegalStateException("Errore Ollama ${response.statusCode()}: ${response.body()}")
    }

    val root = JsonSupport.mapper.readValue(response.body(), Any::class.java)
    val rootMap = asStringAnyMap(root)
    val message = asStringAnyMap(rootMap["message"]).toMutableMap()

    if (!message.containsKey("role")) {
        message["role"] = "assistant"
    }
    if (!message.containsKey("content")) {
        message["content"] = ""
    }

    return message
}

private fun toolToOllamaSchema(tool: Map<String, Any?>): Map<String, Any?> {
    return mapOf(
        "type" to "function",
        "function" to mapOf(
            "name" to (tool["name"]?.toString() ?: ""),
            "description" to (tool["description"]?.toString() ?: ""),
            "parameters" to (tool["inputSchema"] ?: mapOf("type" to "object", "properties" to emptyMap<String, Any>())),
        ),
    )
}

private fun parseArguments(raw: Any?): Map<String, Any?> {
    return when (raw) {
        null -> emptyMap()
        is Map<*, *> -> raw.entries
            .filter { it.key is String }
            .associate { (key, value) -> key.toString() to value }

        is String -> {
            val candidate = raw.trim()
            if (candidate.isBlank()) {
                emptyMap()
            } else {
                try {
                    asStringAnyMap(JsonSupport.mapper.readValue(candidate, Any::class.java))
                } catch (_: Exception) {
                    emptyMap()
                }
            }
        }

        else -> emptyMap()
    }
}

private fun toolResultToText(toolResult: Map<String, Any?>): String {
    val content = toolResult["content"] as? List<*>
        ?: return JsonSupport.mapper.writeValueAsString(toolResult)

    val parts = content.map { item ->
        val itemMap = asStringAnyMap(item)
        if (itemMap["type"] == "text") {
            itemMap["text"]?.toString().orEmpty()
        } else {
            JsonSupport.mapper.writeValueAsString(item)
        }
    }

    return parts.joinToString("\n")
}

private fun asStringAnyMap(value: Any?): Map<String, Any?> {
    if (value !is Map<*, *>) {
        return emptyMap()
    }

    return value.entries
        .filter { it.key is String }
        .associate { (key, mapValue) -> key.toString() to mapValue }
}
