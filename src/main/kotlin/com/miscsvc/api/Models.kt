package com.miscsvc.api

import java.math.BigDecimal
import java.time.OffsetDateTime

data class CustomerCreate(
    val name: String,
    val email: String,
)

data class CustomerOut(
    val id: Int,
    val name: String,
    val email: String,
    val createdAt: OffsetDateTime,
)

data class OrderCreate(
    val customerId: Int,
    val item: String,
    val amount: BigDecimal,
    val status: String = "new",
)

data class OrderOut(
    val id: Int,
    val customerId: Int,
    val item: String,
    val amount: BigDecimal,
    val status: String,
    val createdAt: OffsetDateTime,
)

data class ErrorResponse(
    val detail: String,
)

class DuplicateEmailException : RuntimeException("Email gia presente")

class CustomerNotFoundException(val customerId: Int) : RuntimeException(
    "Customer con id=$customerId non trovato",
)

class ValidationException(message: String) : RuntimeException(message)
