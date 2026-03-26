package com.miscsvc.api

private val SIMPLE_EMAIL_REGEX = Regex("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$")

fun validateCustomerPayload(payload: CustomerCreate) {
    if (payload.name.isBlank() || payload.name.length > 120) {
        throw ValidationException("name deve avere lunghezza tra 1 e 120")
    }

    if (!SIMPLE_EMAIL_REGEX.matches(payload.email)) {
        throw ValidationException("email non valida")
    }
}

fun validateOrderPayload(payload: OrderCreate) {
    if (payload.customerId <= 0) {
        throw ValidationException("customer_id deve essere > 0")
    }

    if (payload.item.isBlank() || payload.item.length > 120) {
        throw ValidationException("item deve avere lunghezza tra 1 e 120")
    }

    if (payload.amount.signum() < 0) {
        throw ValidationException("amount deve essere >= 0")
    }

    if (payload.status.isBlank() || payload.status.length > 40) {
        throw ValidationException("status deve avere lunghezza tra 1 e 40")
    }
}
