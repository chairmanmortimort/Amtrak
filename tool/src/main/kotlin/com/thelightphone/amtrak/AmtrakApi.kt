package com.thelightphone.amtrak

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import kotlinx.serialization.builtins.ListSerializer

private const val BASE_URL = "https://api.amtraker.com/v2"

internal class AmtrakApi {
    private val json = Json { ignoreUnknownKeys = true }
    private val client = HttpClient(OkHttp) {
        install(ContentNegotiation) {
            json(json)
        }
    }

    /** Fetch all trains currently active or planned.
     *  Returns pairs of (trainId, trainData) so callers can fetch exact details via /trains/{trainId}. */
    suspend fun fetchAllTrains(): Result<List<Pair<String, TrainData>>> = runCatching {
        val response = client.get("$BASE_URL/trains")
        if (!response.status.isSuccess()) {
            throw ApiException("HTTP ${response.status.value}: ${response.bodyAsText().take(200)}")
        }
        val map = response.body<Map<String, List<TrainData>>>()
        map.entries.map { (id, trains) -> id to trains.first() }
    }

    /** Fetch details for a specific train by its ID/key.
     *  Endpoint is inconsistent: returns a bare JSON array (possibly empty) when no
     *  active train matches, or a Map<num, List<TrainData>> when found. Handle both. */
    suspend fun fetchTrain(trainId: String): Result<List<TrainData>> = runCatching {
        val response = client.get("$BASE_URL/trains/$trainId")
        if (!response.status.isSuccess()) {
            throw ApiException("HTTP ${response.status.value}: ${response.bodyAsText().take(200)}")
        }
        val element = json.parseToJsonElement(response.bodyAsText())
        when {
            element is kotlinx.serialization.json.JsonArray -> {
                json.decodeFromJsonElement(ListSerializer(TrainData.serializer()), element)
            }
            element is kotlinx.serialization.json.JsonObject -> {
                element.values.flatMap { entry ->
                    json.decodeFromJsonElement(ListSerializer(TrainData.serializer()), entry)
                }
            }
            else -> emptyList()
        }
    }

    /** Fetch all stations with their details. */
    suspend fun fetchAllStations(): Result<Map<String, Station>> = runCatching {
        val response = client.get("$BASE_URL/stations")
        if (!response.status.isSuccess()) {
            throw ApiException("HTTP ${response.status.value}: ${response.bodyAsText().take(200)}")
        }
        response.body<Map<String, Station>>()
    }

    /**
     * Fetch a single station and its upcoming train IDs.
     * Endpoint returns Map<code, Station> where Station.trains is List<String> of train IDs.
     */
    suspend fun fetchStationTrains(stationCode: String): Result<Station> = runCatching {
        val response = client.get("$BASE_URL/stations/$stationCode")
        if (!response.status.isSuccess()) {
            throw ApiException("HTTP ${response.status.value}: ${response.bodyAsText().take(200)}")
        }
        val map = response.body<Map<String, Station>>()
        map[stationCode]
            ?: throw ApiException("Station $stationCode not found in response")
    }

    fun close() {
        client.close()
    }
}

internal class ApiException(message: String) : Exception(message)
