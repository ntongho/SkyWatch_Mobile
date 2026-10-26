package com.harbour.skywatch

import android.content.Context
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.net.URLEncoder
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.net.UnknownHostException
import java.net.ConnectException
import javax.net.ssl.SSLException
import java.io.IOException
import java.io.InterruptedIOException

/** All requests go through the hosted FastAPI service; the app never connects to the DB directly. */
object SkyApi {
    private const val BASE_URL = BuildConfig.API_BASE_URL
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .callTimeout(90, TimeUnit.SECONDS)
        .build()
    private val jsonType = "application/json; charset=utf-8".toMediaType()

    fun stableClientId(context: Context): String {
        val prefs = context.getSharedPreferences("sky_watch", Context.MODE_PRIVATE)
        return prefs.getString("client_id", null) ?: UUID.randomUUID().toString().replace("-", "").also {
            prefs.edit().putString("client_id", it).apply()
        }
    }

    fun friendlyError(error: Throwable): String {
        val causes = generateSequence(error) { it.cause }.toList()
        val message = error.message.orEmpty()
        return when {
            causes.any { it is UnknownHostException } -> "No internet connection or Sky Watch’s address could not be found. Check your connection and try again."
            causes.any { it is InterruptedIOException } -> "Sky Watch is taking too long to respond. The hosted service may be waking up—please try again shortly."
            causes.any { it is ConnectException } -> "Could not connect to Sky Watch. Check your internet connection and try again."
            causes.any { it is SSLException } -> "A secure connection to Sky Watch could not be established. Please try again."
            message.startsWith("API ") -> "Sky Watch returned an error: ${message.substringAfter(":").trim()}"
            causes.any { it is IOException } -> "A connection problem interrupted the request. Check your internet connection and retry."
            else -> "Something went wrong while loading Sky Watch. Please try again."
        }
    }

    private fun request(path: String, method: String = "GET", body: JSONObject? = null, clientId: String? = null): JSONObject {
        val builder = Request.Builder().url(BASE_URL.trimEnd('/') + path)
            .header("Accept", "application/json")
        if (clientId != null) builder.header("X-Client-Id", clientId)
        if (method == "POST") builder.post((body ?: JSONObject()).toString().toRequestBody(jsonType))
        else builder.get()
        client.newCall(builder.build()).execute().use { response ->
            val responseText = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                val message = try { JSONObject(responseText).optString("detail", responseText) } catch (_: Exception) { responseText }
                error("API ${response.code}: $message")
            }
            return JSONObject(responseText)
        }
    }

    private fun enc(value: String) = URLEncoder.encode(value, "UTF-8")
    fun health() = request("/api/health")
    fun geocode(query: String) = request("/api/geocode?q=${enc(query)}&limit=5")
    fun weather(lat: Double, lon: Double) = request("/api/weather?lat=$lat&lon=$lon")
    fun airQuality(lat: Double, lon: Double) = request("/api/air-quality?lat=$lat&lon=$lon")
    fun issPosition(lat: Double? = null, lon: Double? = null): JSONObject {
        val query = if (lat != null && lon != null) "?lat=$lat&lon=$lon" else ""
        return request("/api/iss/position$query")
    }
    fun issPasses(lat: Double, lon: Double) = request("/api/iss/passes?lat=$lat&lon=$lon&limit=5")
    fun apod(date: String? = null): JSONObject {
        val query = date?.let { "?date=${enc(it)}" } ?: ""
        return request("/api/apod$query")
    }
    fun apodBackground() = request("/api/apod/background")

    /** Persists this place lookup in the backend's database via POST /api/history. */
    fun recordLookup(clientId: String, city: String, state: String?, country: String, lat: Double, lon: Double) =
        request("/api/history", "POST", JSONObject().apply {
            put("city", city); put("state", state ?: JSONObject.NULL); put("country", country)
            put("lat", lat); put("lon", lon)
        }, clientId)

    fun history(clientId: String) = request("/api/history?limit=50", clientId = clientId)
    fun historyStats(clientId: String) = request("/api/history/stats", clientId = clientId)
}
