package com.miscsvc

import com.miscsvc.api.runApiServer
import com.miscsvc.config.AppSettings
import com.miscsvc.db.bootstrapDatabase
import com.miscsvc.json.JsonSupport
import com.miscsvc.mcp.client.runMcpClient
import com.miscsvc.mcp.server.runMcpServer

private const val USAGE = """
Usage:
  java -jar build/libs/mcp-trial-kotlin-all.jar api
  java -jar build/libs/mcp-trial-kotlin-all.jar bootstrap-db
  java -jar build/libs/mcp-trial-kotlin-all.jar mcp-server
  java -jar build/libs/mcp-trial-kotlin-all.jar mcp-client [prompt...]

Comandi helper equivalenti:
  ./scripts/run-api.sh
  ./scripts/bootstrap-db.sh
  ./scripts/run-mcp-server.sh
  ./scripts/run-mcp-client.sh [prompt...]
"""

fun main(args: Array<String>) {
    val settings = AppSettings.load()

    when (val command = args.firstOrNull()?.lowercase()) {
        "api" -> runApiServer(settings)
        "bootstrap-db" -> {
            val result = bootstrapDatabase(settings)
            println(JsonSupport.mapper.writeValueAsString(result))
        }

        "mcp-server" -> runMcpServer(settings)
        "mcp-client" -> runMcpClient(settings, args.drop(1))

        null, "help", "--help", "-h" -> println(USAGE.trimIndent())
        else -> {
            System.err.println("Comando non valido: $command")
            println(USAGE.trimIndent())
        }
    }
}
