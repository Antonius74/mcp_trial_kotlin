package com.miscsvc.db

import com.miscsvc.config.AppSettings

data class BootstrapResult(
    val database: String,
    val createdDatabase: Boolean,
    val tables: List<String>,
)

fun bootstrapDatabase(settings: AppSettings): BootstrapResult {
    val createdDatabase = createDatabaseIfMissing(settings)
    createTablesAndSeed(settings)

    return BootstrapResult(
        database = settings.postgresDb,
        createdDatabase = createdDatabase,
        tables = listOf("customers", "orders"),
    )
}

private fun createDatabaseIfMissing(settings: AppSettings): Boolean {
    DatabaseConnections.adminConnection(settings).use { connection ->
        connection.autoCommit = true

        connection.prepareStatement("SELECT 1 FROM pg_database WHERE datname = ?").use { checkStatement ->
            checkStatement.setString(1, settings.postgresDb)
            checkStatement.executeQuery().use { resultSet ->
                if (resultSet.next()) {
                    return false
                }
            }
        }

        connection.createStatement().use { statement ->
            statement.execute("CREATE DATABASE ${quoteIdentifier(settings.postgresDb)}")
        }

        return true
    }
}

private fun createTablesAndSeed(settings: AppSettings) {
    val createTablesSql = listOf(
        """
        CREATE TABLE IF NOT EXISTS customers (
            id SERIAL PRIMARY KEY,
            name TEXT NOT NULL,
            email TEXT UNIQUE NOT NULL,
            created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
        );
        """.trimIndent(),
        """
        CREATE TABLE IF NOT EXISTS orders (
            id SERIAL PRIMARY KEY,
            customer_id INTEGER NOT NULL REFERENCES customers(id) ON DELETE CASCADE,
            item TEXT NOT NULL,
            amount NUMERIC(10,2) NOT NULL CHECK (amount >= 0),
            status TEXT NOT NULL DEFAULT 'new',
            created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
        );
        """.trimIndent(),
    )

    val seedSql = listOf(
        """
        INSERT INTO customers (name, email)
        VALUES
            ('Mario Rossi', 'mario.rossi@example.com'),
            ('Laura Bianchi', 'laura.bianchi@example.com')
        ON CONFLICT (email) DO NOTHING;
        """.trimIndent(),
        """
        INSERT INTO orders (customer_id, item, amount, status)
        SELECT c.id, 'Notebook', 1299.00, 'paid'
        FROM customers c
        WHERE c.email = 'mario.rossi@example.com'
          AND NOT EXISTS (
              SELECT 1 FROM orders o
              WHERE o.customer_id = c.id AND o.item = 'Notebook'
          );
        """.trimIndent(),
        """
        INSERT INTO orders (customer_id, item, amount, status)
        SELECT c.id, 'Mouse', 39.90, 'new'
        FROM customers c
        WHERE c.email = 'laura.bianchi@example.com'
          AND NOT EXISTS (
              SELECT 1 FROM orders o
              WHERE o.customer_id = c.id AND o.item = 'Mouse'
          );
        """.trimIndent(),
    )

    DatabaseConnections.appConnection(settings).use { connection ->
        connection.autoCommit = false

        connection.createStatement().use { statement ->
            createTablesSql.forEach(statement::execute)
            seedSql.forEach(statement::execute)
        }

        connection.commit()
    }
}

private fun quoteIdentifier(identifier: String): String {
    return "\"${identifier.replace("\"", "\"\"")}\""
}
