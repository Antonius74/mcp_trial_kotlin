package com.miscsvc.config

import java.nio.file.Path
import kotlin.io.path.absolute
import kotlin.io.path.exists

private const val DEFAULT_MCP_SERVER_SCRIPT = "./scripts/run-mcp-server.sh"

data class AppSettings(
    val postgresHost: String,
    val postgresPort: Int,
    val postgresUser: String,
    val postgresPassword: String,
    val postgresDb: String,
    val postgresAdminDb: String,
    val apiHost: String,
    val apiPort: Int,
    val apiBaseUrl: String,
    val ollamaUrl: String,
    val ollamaModel: String,
    val mcpServerCommand: String,
    val mcpServerArgs: List<String>,
) {
    companion object {
        fun load(root: Path = Path.of(".").absolute()): AppSettings {
            val dotenv = EnvLoader.load(root.resolve(".env"))

            fun read(name: String, default: String): String {
                return System.getenv(name)
                    ?: dotenv[name]
                    ?: default
            }

            val defaultServerCommand = if (root.resolve("scripts/run-mcp-server.sh").exists()) {
                DEFAULT_MCP_SERVER_SCRIPT
            } else {
                "java"
            }

            return AppSettings(
                postgresHost = read("POSTGRES_HOST", "127.0.0.1"),
                postgresPort = read("POSTGRES_PORT", "5432").toIntOrNull() ?: 5432,
                postgresUser = read("POSTGRES_USER", "postgres"),
                postgresPassword = read("POSTGRES_PASSWORD", "postgres"),
                postgresDb = read("POSTGRES_DB", "misc_svc"),
                postgresAdminDb = read("POSTGRES_ADMIN_DB", "postgres"),
                apiHost = read("API_HOST", "0.0.0.0"),
                apiPort = read("API_PORT", "8000").toIntOrNull() ?: 8000,
                apiBaseUrl = read("API_BASE_URL", "http://127.0.0.1:8000"),
                ollamaUrl = read("OLLAMA_URL", "http://127.0.0.1:11434"),
                ollamaModel = read("OLLAMA_MODEL", "gpt-oss:120b-cloud"),
                mcpServerCommand = read("MCP_SERVER_COMMAND", defaultServerCommand),
                mcpServerArgs = splitCommandArgs(read("MCP_SERVER_ARGS", "")),
            )
        }

        private fun splitCommandArgs(raw: String): List<String> {
            if (raw.isBlank()) {
                return emptyList()
            }

            val tokens = mutableListOf<String>()
            val current = StringBuilder()
            var inSingle = false
            var inDouble = false
            var escaped = false

            for (char in raw) {
                when {
                    escaped -> {
                        current.append(char)
                        escaped = false
                    }

                    char == '\\' && !inSingle -> {
                        escaped = true
                    }

                    char == '\'' && !inDouble -> {
                        inSingle = !inSingle
                    }

                    char == '"' && !inSingle -> {
                        inDouble = !inDouble
                    }

                    char.isWhitespace() && !inSingle && !inDouble -> {
                        if (current.isNotEmpty()) {
                            tokens.add(current.toString())
                            current.setLength(0)
                        }
                    }

                    else -> current.append(char)
                }
            }

            if (current.isNotEmpty()) {
                tokens.add(current.toString())
            }

            return tokens
        }
    }
}
