package org.wizjer.android.data.api

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.wizjer.android.BuildConfig
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** Status code and raw body of an API response. */
data class ApiResponse(val status: Int, val body: String) {
    val isSuccess: Boolean get() = status in 200..299
    fun json(): JSONObject = JSONObject(body)
}

/**
 * Minimal JSON-over-HTTPS client for the Wizjer API. The base URL comes from the build
 * (see README: "API address"); release builds only allow HTTPS.
 */
class ApiClient(private val baseUrl: String = BuildConfig.API_BASE_URL) {

    @Throws(IOException::class)
    suspend fun get(path: String, token: String? = null): ApiResponse = send("GET", path, null, token)

    @Throws(IOException::class)
    suspend fun post(path: String, body: JSONObject, token: String? = null): ApiResponse = send("POST", path, body, token)

    private suspend fun send(method: String, path: String, body: JSONObject?, token: String?): ApiResponse =
        withContext(Dispatchers.IO) {
            val connection = URL(baseUrl + path).openConnection() as HttpURLConnection
            try {
                connection.requestMethod = method
                connection.connectTimeout = TIMEOUT_MILLIS
                connection.readTimeout = TIMEOUT_MILLIS
                connection.setRequestProperty("Accept", "application/json")
                if (token != null) connection.setRequestProperty("Authorization", "Bearer $token")
                if (body != null) {
                    connection.doOutput = true
                    connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                    connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
                }
                val status = connection.responseCode
                val stream = if (status >= 400) connection.errorStream else connection.inputStream
                ApiResponse(status, stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty())
            } finally {
                connection.disconnect()
            }
        }

    private companion object {
        const val TIMEOUT_MILLIS = 15_000
    }
}
