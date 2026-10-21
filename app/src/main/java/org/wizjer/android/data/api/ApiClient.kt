package org.wizjer.android.data.api

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.wizjer.android.BuildConfig
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

/** Status code and raw body of an API response. */
data class ApiResponse(val status: Int, val body: String) {
    val isSuccess: Boolean get() = status in 200..299
    fun json(): JSONObject = JSONObject(body)
}

/** A large response, read as a stream. [etag] identifies this version of the content. */
class StreamedResponse(val status: Int, val etag: String?, val body: InputStream?) {
    val isSuccess: Boolean get() = status in 200..299
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

    /**
     * GET for large downloads: [read] consumes the body as it arrives (gzip is decoded
     * transparently). With [etag], an unchanged resource answers 304 and no body.
     */
    @Throws(IOException::class)
    suspend fun <T> stream(path: String, etag: String? = null, read: (StreamedResponse) -> T): T =
        withContext(Dispatchers.IO) {
            val connection = open(path)
            try {
                if (etag != null) connection.setRequestProperty("If-None-Match", etag)
                val status = connection.responseCode
                val body = if (status in 200..299) connection.inputStream else null
                body.use { read(StreamedResponse(status, connection.getHeaderField("ETag"), it)) }
            } finally {
                connection.disconnect()
            }
        }

    private fun open(path: String): HttpURLConnection =
        (URL(baseUrl + path).openConnection() as HttpURLConnection).apply {
            connectTimeout = TIMEOUT_MILLIS
            readTimeout = TIMEOUT_MILLIS
            setRequestProperty("Accept", "application/json")
        }

    private suspend fun send(method: String, path: String, body: JSONObject?, token: String?): ApiResponse =
        withContext(Dispatchers.IO) {
            val connection = open(path)
            try {
                connection.requestMethod = method
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
