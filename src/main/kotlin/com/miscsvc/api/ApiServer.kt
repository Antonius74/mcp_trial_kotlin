package com.miscsvc.api

import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.databind.util.StdDateFormat
import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.miscsvc.config.AppSettings
import com.miscsvc.db.bootstrapDatabase
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.jackson.jackson
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.plugins.statuspages.exception
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing

fun runApiServer(settings: AppSettings) {
    bootstrapDatabase(settings)

    embeddedServer(
        factory = Netty,
        host = settings.apiHost,
        port = settings.apiPort,
    ) {
        configureApi(settings)
    }.start(wait = true)
}

private fun Application.configureApi(settings: AppSettings) {
    val repository = ApiRepository(settings)

    install(ContentNegotiation) {
        jackson {
            registerKotlinModule()
            findAndRegisterModules()
            propertyNamingStrategy = PropertyNamingStrategies.SNAKE_CASE
            disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            dateFormat = StdDateFormat().withColonInTimeZone(true)
        }
    }

    install(StatusPages) {
        exception<ValidationException> { call, exception ->
            call.respond(HttpStatusCode.UnprocessableEntity, ErrorResponse(exception.message ?: "Payload non valido"))
        }

        exception<DuplicateEmailException> { call, _ ->
            call.respond(HttpStatusCode.Conflict, ErrorResponse("Email gia presente"))
        }

        exception<CustomerNotFoundException> { call, exception ->
            call.respond(HttpStatusCode.NotFound, ErrorResponse(exception.message ?: "Customer non trovato"))
        }

        exception<BadRequestException> { call, _ ->
            call.respond(HttpStatusCode.BadRequest, ErrorResponse("Body JSON non valido"))
        }

        exception<Throwable> { call, _ ->
            call.respond(HttpStatusCode.InternalServerError, ErrorResponse("Errore interno"))
        }
    }

    routing {
        get("/health") {
            call.respond(mapOf("status" to "ok"))
        }

        get("/customers") {
            call.respond(repository.listCustomers())
        }

        post("/customers") {
            val payload = call.receive<CustomerCreate>()
            validateCustomerPayload(payload)
            call.respond(HttpStatusCode.Created, repository.createCustomer(payload))
        }

        get("/orders") {
            call.respond(repository.listOrders())
        }

        post("/orders") {
            val payload = call.receive<OrderCreate>()
            validateOrderPayload(payload)
            call.respond(HttpStatusCode.Created, repository.createOrder(payload))
        }
    }
}
