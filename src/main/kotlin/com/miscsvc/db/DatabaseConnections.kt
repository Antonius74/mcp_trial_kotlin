package com.miscsvc.db

import com.miscsvc.config.AppSettings
import java.sql.Connection
import java.sql.DriverManager

object DatabaseConnections {
    init {
        Class.forName("org.postgresql.Driver")
    }

    fun appConnection(settings: AppSettings): Connection {
        return DriverManager.getConnection(
            jdbcUrl(settings.postgresHost, settings.postgresPort, settings.postgresDb),
            settings.postgresUser,
            settings.postgresPassword,
        )
    }

    fun adminConnection(settings: AppSettings): Connection {
        return DriverManager.getConnection(
            jdbcUrl(settings.postgresHost, settings.postgresPort, settings.postgresAdminDb),
            settings.postgresUser,
            settings.postgresPassword,
        )
    }

    private fun jdbcUrl(host: String, port: Int, database: String): String {
        return "jdbc:postgresql://$host:$port/$database"
    }
}
