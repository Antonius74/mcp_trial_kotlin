package com.miscsvc.api

import com.miscsvc.config.AppSettings
import com.miscsvc.db.DatabaseConnections
import java.sql.ResultSet
import java.sql.SQLException
import java.time.OffsetDateTime

private const val UNIQUE_VIOLATION_SQLSTATE = "23505"
private const val FOREIGN_KEY_VIOLATION_SQLSTATE = "23503"

class ApiRepository(private val settings: AppSettings) {
    fun listCustomers(): List<CustomerOut> {
        DatabaseConnections.appConnection(settings).use { connection ->
            connection.prepareStatement(
                """
                SELECT id, name, email, created_at
                FROM customers
                ORDER BY id ASC
                """.trimIndent(),
            ).use { statement ->
                statement.executeQuery().use { resultSet ->
                    return generateSequence {
                        if (resultSet.next()) {
                            mapCustomer(resultSet)
                        } else {
                            null
                        }
                    }.toList()
                }
            }
        }
    }

    fun createCustomer(payload: CustomerCreate): CustomerOut {
        try {
            DatabaseConnections.appConnection(settings).use { connection ->
                connection.autoCommit = false
                connection.prepareStatement(
                    """
                    INSERT INTO customers (name, email)
                    VALUES (?, ?)
                    RETURNING id, name, email, created_at
                    """.trimIndent(),
                ).use { statement ->
                    statement.setString(1, payload.name)
                    statement.setString(2, payload.email)
                    statement.executeQuery().use { resultSet ->
                        if (resultSet.next()) {
                            val created = mapCustomer(resultSet)
                            connection.commit()
                            return created
                        }
                    }
                }

                connection.rollback()
            }
        } catch (exception: SQLException) {
            if (exception.sqlState == UNIQUE_VIOLATION_SQLSTATE) {
                throw DuplicateEmailException()
            }
            throw exception
        }

        error("Impossibile creare customer")
    }

    fun listOrders(): List<OrderOut> {
        DatabaseConnections.appConnection(settings).use { connection ->
            connection.prepareStatement(
                """
                SELECT id, customer_id, item, amount, status, created_at
                FROM orders
                ORDER BY id ASC
                """.trimIndent(),
            ).use { statement ->
                statement.executeQuery().use { resultSet ->
                    return generateSequence {
                        if (resultSet.next()) {
                            mapOrder(resultSet)
                        } else {
                            null
                        }
                    }.toList()
                }
            }
        }
    }

    fun createOrder(payload: OrderCreate): OrderOut {
        try {
            DatabaseConnections.appConnection(settings).use { connection ->
                connection.autoCommit = false
                connection.prepareStatement(
                    """
                    INSERT INTO orders (customer_id, item, amount, status)
                    VALUES (?, ?, ?, ?)
                    RETURNING id, customer_id, item, amount, status, created_at
                    """.trimIndent(),
                ).use { statement ->
                    statement.setInt(1, payload.customerId)
                    statement.setString(2, payload.item)
                    statement.setBigDecimal(3, payload.amount)
                    statement.setString(4, payload.status)
                    statement.executeQuery().use { resultSet ->
                        if (resultSet.next()) {
                            val created = mapOrder(resultSet)
                            connection.commit()
                            return created
                        }
                    }
                }

                connection.rollback()
            }
        } catch (exception: SQLException) {
            if (exception.sqlState == FOREIGN_KEY_VIOLATION_SQLSTATE) {
                throw CustomerNotFoundException(payload.customerId)
            }
            throw exception
        }

        error("Impossibile creare ordine")
    }

    private fun mapCustomer(resultSet: ResultSet): CustomerOut {
        return CustomerOut(
            id = resultSet.getInt("id"),
            name = resultSet.getString("name"),
            email = resultSet.getString("email"),
            createdAt = resultSet.getObject("created_at", OffsetDateTime::class.java),
        )
    }

    private fun mapOrder(resultSet: ResultSet): OrderOut {
        return OrderOut(
            id = resultSet.getInt("id"),
            customerId = resultSet.getInt("customer_id"),
            item = resultSet.getString("item"),
            amount = resultSet.getBigDecimal("amount"),
            status = resultSet.getString("status"),
            createdAt = resultSet.getObject("created_at", OffsetDateTime::class.java),
        )
    }
}
