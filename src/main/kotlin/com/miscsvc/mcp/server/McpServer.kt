package com.miscsvc.mcp.server

import com.miscsvc.config.AppSettings
import com.miscsvc.json.JsonSupport
import com.miscsvc.mcp.protocol.readJsonRpcMessage
import com.miscsvc.mcp.protocol.writeJsonRpcMessage
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

private const val SERVER_NAME = "misc-service"
private const val SERVER_VERSION = "1.0.0"
private const val PROTOCOL_VERSION = "2024-11-05"

private typealias ToolHandler = (Map<String, Any?>) -> Map<String, Any?>

data class ToolDefinition(
    val description: String,
    val inputSchema: Map<String, Any?>,
    val handler: ToolHandler,
)

class McpServer(private val settings: AppSettings) {
    private val input = BufferedInputStream(System.`in`)
    private val output = BufferedOutputStream(System.out)
    private val httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(20))
        .build()

    private val toolDefinitions: Map<String, ToolDefinition> = mapOf(
        "health_check" to ToolDefinition(
            description = "Ritorna lo stato della REST API mock.",
            inputSchema = mapOf(
                "type" to "object",
                "properties" to emptyMap<String, Any>(),
                "additionalProperties" to false,
            ),
            handler = { _ -> callApi("GET", "/health") },
        ),
        "list_customers" to ToolDefinition(
            description = "Legge tutti i clienti dal servizio REST.",
            inputSchema = mapOf(
                "type" to "object",
                "properties" to emptyMap<String, Any>(),
                "additionalProperties" to false,
            ),
            handler = { _ -> callApi("GET", "/customers") },
        ),
        "create_customer" to ToolDefinition(
            description = "Crea un nuovo cliente nel servizio REST.",
            inputSchema = mapOf(
                "type" to "object",
                "properties" to mapOf(
                    "name" to mapOf("type" to "string", "description" to "Nome cliente"),
                    "email" to mapOf("type" to "string", "description" to "Email cliente"),
                ),
                "required" to listOf("name", "email"),
                "additionalProperties" to false,
            ),
            handler = { arguments ->
                callApi(
                    method = "POST",
                    path = "/customers",
                    payload = mapOf(
                        "name" to arguments["name"],
                        "email" to arguments["email"],
                    ),
                )
            },
        ),
        "list_orders" to ToolDefinition(
            description = "Legge tutti gli ordini dal servizio REST.",
            inputSchema = mapOf(
                "type" to "object",
                "properties" to emptyMap<String, Any>(),
                "additionalProperties" to false,
            ),
            handler = { _ -> callApi("GET", "/orders") },
        ),
        "create_order" to ToolDefinition(
            description = "Crea un nuovo ordine nel servizio REST.",
            inputSchema = mapOf(
                "type" to "object",
                "properties" to mapOf(
                    "customer_id" to mapOf("type" to "integer", "description" to "ID cliente"),
                    "item" to mapOf("type" to "string", "description" to "Nome prodotto"),
                    "amount" to mapOf("type" to "number", "description" to "Importo ordine"),
                    "status" to mapOf("type" to "string", "description" to "Stato ordine"),
                ),
                "required" to listOf("customer_id", "item", "amount"),
                "additionalProperties" to false,
            ),
            handler = { arguments ->
                callApi(
                    method = "POST",
                    path = "/orders",
                    payload = mapOf(
                        "customer_id" to arguments["customer_id"],
                        "item" to arguments["item"],
                        "amount" to arguments["amount"],
                        "status" to (arguments["status"] ?: "new"),
                    ),
                )
            },
        ),
    )

    fun run() {
        while (true) {
            val message = readJsonRpcMessage(input) ?: break
            val messageId = message["id"]
            val method = message["method"]?.toString()?.trim().orEmpty()
            val params = asStringAnyMap(message["params"])

            if (method.isBlank()) {
                continue
            }

            val isNotification = messageId == null
            if (isNotification) {
                continue
            }

            try {
                val result = handleRequest(method, params)
                writeJsonRpcMessage(
                    output,
                    mapOf(
                        "jsonrpc" to "2.0",
                        "id" to messageId,
                        "result" to result,
                    ),
                )
            } catch (exception: NotImplementedError) {
                writeJsonRpcMessage(
                    output,
                    mapOf(
                        "jsonrpc" to "2.0",
                        "id" to messageId,
                        "error" to mapOf(
                            "code" to -32601,
                            "message" to (exception.message ?: "Metodo non supportato"),
                        ),
                    ),
                )
            } catch (exception: Exception) {
                writeJsonRpcMessage(
                    output,
                    mapOf(
                        "jsonrpc" to "2.0",
                        "id" to messageId,
                        "error" to mapOf(
                            "code" to -32603,
                            "message" to (exception.message ?: "Errore interno"),
                        ),
                    ),
                )
            }
        }
    }

    private fun handleRequest(method: String, params: Map<String, Any?>): Any {
        return when (method) {
            "initialize" -> mapOf(
                "protocolVersion" to PROTOCOL_VERSION,
                "capabilities" to mapOf("tools" to emptyMap<String, Any>()),
                "serverInfo" to mapOf(
                    "name" to SERVER_NAME,
                    "version" to SERVER_VERSION,
                ),
            )

            "tools/list" -> mapOf(
                "tools" to toolDefinitions.map { (name, meta) ->
                    mapOf(
                        "name" to name,
                        "description" to meta.description,
                        "inputSchema" to meta.inputSchema,
                    )
                },
            )

            "tools/call" -> {
                val name = params["name"]?.toString()
                    ?: throw IllegalArgumentException("Nome tool mancante")

                val arguments = asStringAnyMap(params["arguments"])
                val definition = toolDefinitions[name]
                    ?: throw IllegalArgumentException("Tool non trovato: $name")

                val toolResult = definition.handler(arguments)
                mapOf(
                    "content" to listOf(
                        mapOf(
                            "type" to "text",
                            "text" to JsonSupport.mapper.writeValueAsString(toolResult),
                        ),
                    ),
                )
            }

            "ping" -> emptyMap<String, Any>()
            else -> throw NotImplementedError("Metodo non supportato: $method")
        }
    }

    private fun callApi(method: String, path: String, payload: Map<String, Any?>? = null): Map<String, Any?> {
        val normalizedPath = if (path.startsWith('/')) path else "/$path"
        val url = settings.apiBaseUrl.trimEnd('/') + normalizedPath

        val requestBuilder = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .timeout(Duration.ofSeconds(20))
            .header("Accept", "application/json")

        when (method.uppercase()) {
            "GET" -> requestBuilder.GET()
            "POST" -> {
                val jsonBody = JsonSupport.mapper.writeValueAsString(payload ?: emptyMap<String, Any?>())
                requestBuilder
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
            }

            else -> throw IllegalArgumentException("HTTP method non supportato: $method")
        }

        val response = httpClient.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString())
        val statusCode = response.statusCode()
        val responseBody = response.body()

        if (statusCode >= 400) {
            return mapOf(
                "ok" to false,
                "status_code" to statusCode,
                "error" to responseBody,
            )
        }

        return mapOf(
            "ok" to true,
            "status_code" to statusCode,
            "data" to parseJsonOrText(responseBody),
        )
    }

    private fun parseJsonOrText(raw: String): Any {
        return try {
            JsonSupport.mapper.readValue(raw, Any::class.java)
        } catch (_: Exception) {
            raw
        }
    }

    private fun asStringAnyMap(value: Any?): Map<String, Any?> {
        if (value !is Map<*, *>) {
            return emptyMap()
        }

        return value.entries
            .filter { it.key is String }
            .associate { (key, mapValue) -> key.toString() to mapValue }
    }
}

fun runMcpServer(settings: AppSettings) {
    McpServer(settings).run()
}
