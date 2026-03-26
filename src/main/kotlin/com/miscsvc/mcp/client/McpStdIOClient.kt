package com.miscsvc.mcp.client

import com.miscsvc.config.AppSettings
import com.miscsvc.mcp.protocol.readJsonRpcMessage
import com.miscsvc.mcp.protocol.writeJsonRpcMessage
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.time.Duration

private const val PROTOCOL_VERSION = "2024-11-05"

class McpStdIOClient(
    private val command: String,
    private val args: List<String>,
) : AutoCloseable {
    private var process: Process? = null
    private var processInput: BufferedInputStream? = null
    private var processOutput: BufferedOutputStream? = null
    private var requestId: Long = 0

    fun start() {
        if (process != null) {
            return
        }

        val processBuilder = ProcessBuilder(listOf(command) + args)
        val startedProcess = processBuilder.start()
        process = startedProcess
        processInput = BufferedInputStream(startedProcess.inputStream)
        processOutput = BufferedOutputStream(startedProcess.outputStream)

        initialize()
    }

    override fun close() {
        val runningProcess = process ?: return

        if (runningProcess.isAlive) {
            runningProcess.destroy()
            if (!runningProcess.waitFor(Duration.ofSeconds(2).toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS)) {
                runningProcess.destroyForcibly()
            }
        }

        processInput = null
        processOutput = null
        process = null
    }

    fun listTools(): List<Map<String, Any?>> {
        val result = request("tools/list", emptyMap())
        return (result["tools"] as? List<*>)
            ?.mapNotNull { item ->
                if (item is Map<*, *>) {
                    item.entries
                        .filter { it.key is String }
                        .associate { (key, value) -> key.toString() to value }
                } else {
                    null
                }
            }
            ?: emptyList()
    }

    fun callTool(name: String, arguments: Map<String, Any?>): Map<String, Any?> {
        return request(
            "tools/call",
            mapOf(
                "name" to name,
                "arguments" to arguments,
            ),
        )
    }

    private fun initialize() {
        request(
            "initialize",
            mapOf(
                "protocolVersion" to PROTOCOL_VERSION,
                "capabilities" to emptyMap<String, Any>(),
                "clientInfo" to mapOf(
                    "name" to "ollama-mcp-client-kotlin",
                    "version" to "1.0.0",
                ),
            ),
        )

        notify("notifications/initialized", emptyMap())
    }

    private fun request(method: String, params: Map<String, Any?>): Map<String, Any?> {
        requestId += 1
        val currentRequestId = requestId

        sendMessage(
            mapOf(
                "jsonrpc" to "2.0",
                "id" to currentRequestId,
                "method" to method,
                "params" to params,
            ),
        )

        while (true) {
            val message = readMessage()
            if (!matchesRequestId(message["id"], currentRequestId)) {
                continue
            }

            val error = message["error"]
            if (error is Map<*, *>) {
                val code = error["code"]
                val text = error["message"]
                throw IllegalStateException("Errore MCP $code: $text")
            }

            val result = message["result"]
            if (result is Map<*, *>) {
                return result.entries
                    .filter { it.key is String }
                    .associate { (key, value) -> key.toString() to value }
            }

            return emptyMap()
        }
    }

    private fun notify(method: String, params: Map<String, Any?>) {
        sendMessage(
            mapOf(
                "jsonrpc" to "2.0",
                "method" to method,
                "params" to params,
            ),
        )
    }

    private fun sendMessage(payload: Map<String, Any?>) {
        val output = processOutput ?: error("MCP process non avviato")
        writeJsonRpcMessage(output, payload)
    }

    private fun readMessage(): Map<String, Any?> {
        val input = processInput ?: error("MCP process non avviato")
        return readJsonRpcMessage(input)
            ?: throw IllegalStateException(readServerStderrOnExit())
    }

    private fun readServerStderrOnExit(): String {
        val runningProcess = process
        val base = "MCP server terminato"

        if (runningProcess == null) {
            return base
        }

        val stderr = runningProcess.errorStream.bufferedReader().readText().trim()
        if (stderr.isBlank()) {
            return base
        }

        return "$base. STDERR: $stderr"
    }

    private fun matchesRequestId(raw: Any?, expected: Long): Boolean {
        return when (raw) {
            is Int -> raw.toLong() == expected
            is Long -> raw == expected
            is Double -> raw % 1.0 == 0.0 && raw.toLong() == expected
            is Float -> raw % 1f == 0f && raw.toLong() == expected
            is String -> raw == expected.toString()
            else -> false
        }
    }
}

fun defaultMcpStdIOClient(settings: AppSettings): McpStdIOClient {
    return McpStdIOClient(
        command = settings.mcpServerCommand,
        args = settings.mcpServerArgs,
    )
}
