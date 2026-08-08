package com.thelightphone.cdta

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import io.ktor.http.parameters
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

private const val CDTA_API_BASE = "https://api.cdta.org/api"

internal class CdtaApi {
    private val json = Json { ignoreUnknownKeys = true }
    private val client: HttpClient
    private var apiKey: String = ""

    init {
        client = HttpClient(OkHttp) {
            install(ContentNegotiation) {
                json(json)
            }
            defaultRequest {
                headers {
                    append("X-API-KEY", apiKey)
                }
            }
        }
    }

    fun setApiKey(key: String) {
        apiKey = key
    }

    suspend fun fetchRoutes(): Result<List<Route>> = runCatching {
        val response = client.get("$CDTA_API_BASE/routes")
        if (!response.status.isSuccess()) {
            throw ApiException("HTTP ${response.status.value}: ${response.bodyAsText().take(200)}")
        }
        response.body<List<Route>>()
    }

    suspend fun fetchStops(): Result<List<Stop>> = runCatching {
        val response = client.get("$CDTA_API_BASE/stops")
        if (!response.status.isSuccess()) {
            throw ApiException("HTTP ${response.status.value}: ${response.bodyAsText().take(200)}")
        }
        response.body<List<Stop>>()
    }

    suspend fun fetchStopPredictions(stopId: String): Result<StopPredictionResponse> = runCatching {
        val response = client.get("$CDTA_API_BASE/realtime/me/stops/$stopId")
        if (!response.status.isSuccess()) {
            throw ApiException("HTTP ${response.status.value}: ${response.bodyAsText().take(200)}")
        }
        val element = json.parseToJsonElement(response.bodyAsText())
        when {
            element is kotlinx.serialization.json.JsonObject -> {
                json.decodeFromJsonElement(StopPredictionResponse.serializer(), element)
            }
            element is kotlinx.serialization.json.JsonArray -> {
                val list = json.decodeFromJsonElement(
                    ListSerializer(StopPredictionResponse.serializer()),
                    element,
                )
                list.firstOrNull() ?: StopPredictionResponse(stopId = stopId)
            }
            else -> StopPredictionResponse(stopId = stopId)
        }
    }

    suspend fun searchStops(query: String): Result<List<Stop>> = runCatching {
        val response = client.get("$CDTA_API_BASE/stops") {
            url {
                parameters.append("name", query.trim())
            }
        }
        if (!response.status.isSuccess()) {
            throw ApiException("HTTP ${response.status.value}: ${response.bodyAsText().take(200)}")
        }
        response.body<List<Stop>>()
    }

    suspend fun searchRoutes(query: String): Result<List<Route>> = runCatching {
        val response = client.get("$CDTA_API_BASE/routes") {
            url {
                parameters.append("name", query.trim())
            }
        }
        if (!response.status.isSuccess()) {
            throw ApiException("HTTP ${response.status.value}: ${response.bodyAsText().take(200)}")
        }
        response.body<List<Route>>()
    }

    fun close() {
        client.close()
    }
}

internal class ApiException(message: String) : Exception(message)
