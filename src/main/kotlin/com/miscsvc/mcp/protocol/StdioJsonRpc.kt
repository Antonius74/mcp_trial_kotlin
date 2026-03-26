package com.miscsvc.mcp.protocol

import com.fasterxml.jackson.core.type.TypeReference
import com.miscsvc.json.JsonSupport
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream

private val mapTypeReference = object : TypeReference<Map<String, Any?>>() {}

fun readJsonRpcMessage(input: BufferedInputStream): Map<String, Any?>? {
    val headers = mutableMapOf<String, String>()

    while (true) {
        val line = readLine(input) ?: return null
        if (line.isEmpty()) {
            break
        }

        val separator = line.indexOf(':')
        if (separator > 0) {
            val key = line.substring(0, separator).trim().lowercase()
            val value = line.substring(separator + 1).trim()
            headers[key] = value
        }
    }

    val contentLength = headers["content-length"]?.toIntOrNull() ?: return null
    if (contentLength <= 0) {
        return null
    }

    val body = input.readNBytes(contentLength)
    if (body.size != contentLength) {
        return null
    }

    return JsonSupport.mapper.readValue(body, mapTypeReference)
}

fun writeJsonRpcMessage(output: BufferedOutputStream, payload: Map<String, Any?>) {
    val body = JsonSupport.mapper.writeValueAsBytes(payload)
    val header = "Content-Length: ${body.size}\r\n\r\n".toByteArray(Charsets.UTF_8)

    output.write(header)
    output.write(body)
    output.flush()
}

private fun readLine(input: BufferedInputStream): String? {
    val buffer = ByteArrayOutputStream()

    while (true) {
        val read = input.read()
        if (read == -1) {
            return if (buffer.size() == 0) {
                null
            } else {
                buffer.toString(Charsets.UTF_8)
            }
        }

        buffer.write(read)
        if (read == '\n'.code) {
            break
        }
    }

    return buffer
        .toString(Charsets.UTF_8)
        .trimEnd('\r', '\n')
}
