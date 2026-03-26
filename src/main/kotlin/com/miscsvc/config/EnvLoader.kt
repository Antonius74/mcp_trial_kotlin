package com.miscsvc.config

import java.nio.file.Files
import java.nio.file.Path

object EnvLoader {
    fun load(path: Path): Map<String, String> {
        if (!Files.exists(path)) {
            return emptyMap()
        }

        return Files.readAllLines(path)
            .mapNotNull { parseLine(it) }
            .toMap()
    }

    private fun parseLine(rawLine: String): Pair<String, String>? {
        val line = rawLine.trim()
        if (line.isEmpty() || line.startsWith("#")) {
            return null
        }

        val separator = line.indexOf('=')
        if (separator <= 0) {
            return null
        }

        val key = line.substring(0, separator).trim()
        if (key.isEmpty()) {
            return null
        }

        var value = line.substring(separator + 1).trim()
        if (value.length >= 2 &&
            ((value.startsWith('"') && value.endsWith('"')) ||
                (value.startsWith('\'') && value.endsWith('\'')))
        ) {
            value = value.substring(1, value.length - 1)
        }

        return key to value
    }
}
