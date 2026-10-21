package org.wizjer.android.data.account

import org.json.JSONException
import org.json.JSONObject
import org.wizjer.android.data.api.ApiClient
import org.wizjer.android.data.api.ApiResponse
import java.io.IOException
import java.time.LocalDate

/** Raw account requests. Screens use [AccountSession], which also keeps the session and profile. */
internal class AccountApi(private val client: ApiClient = ApiClient()) {

    /** Returns the new session token, or the reason it failed. */
    suspend fun register(registration: Registration): Pair<RegisterResult, String?> = call(
        onNoConnection = RegisterResult.NoConnection to null,
        onFailed = RegisterResult.Failed to null,
    ) {
        val body = JSONObject()
            .put("email", registration.email)
            .put("password", registration.password)
            .put("firstName", registration.firstName)
            .put("lastName", registration.lastName)
            .put("birthDate", registration.birthDate?.toString() ?: JSONObject.NULL)
        val response = client.post("/auth/email/register", body)
        when (response.status) {
            200, 201 -> RegisterResult.Success to response.json().getString("token")
            409 -> RegisterResult.EmailTaken to null
            400, 422 -> RegisterResult.Rejected to null
            else -> RegisterResult.Failed to null
        }
    }

    suspend fun confirmEmail(token: String, code: String): ConfirmResult = call(
        onNoConnection = ConfirmResult.NoConnection,
        onFailed = ConfirmResult.Failed,
    ) {
        when (client.post("/auth/email/code", JSONObject().put("code", code), token).status) {
            200, 204 -> ConfirmResult.Confirmed
            400, 422 -> ConfirmResult.WrongCode
            410 -> ConfirmResult.Expired
            429 -> ConfirmResult.TooManyAttempts
            else -> ConfirmResult.Failed
        }
    }

    suspend fun resendEmailCode(token: String): ResendResult = call(
        onNoConnection = ResendResult.NoConnection,
        onFailed = ResendResult.Failed,
    ) {
        when (client.post("/auth/email/code/resend", JSONObject(), token).status) {
            200, 204 -> ResendResult.Sent
            429 -> ResendResult.TooSoon
            else -> ResendResult.Failed
        }
    }

    /** [identifier] is an email address or a phone number. Returns the session token on success. */
    suspend fun logIn(identifier: String, password: String): Pair<LoginResult, String?> = call(
        onNoConnection = LoginResult.NoConnection to null,
        onFailed = LoginResult.Failed to null,
    ) {
        val isEmail = '@' in identifier
        val body = JSONObject()
            .put(if (isEmail) "email" else "phone", identifier)
            .put("password", password)
        val response = client.post(if (isEmail) "/auth/email/login" else "/auth/phone/login", body)
        when (response.status) {
            200 -> {
                val json = response.json()
                LoginResult.Success(confirmed = json.optBoolean("confirmed", true)) to json.getString("token")
            }
            400, 401, 404 -> LoginResult.InvalidCredentials to null
            else -> LoginResult.Failed to null
        }
    }

    suspend fun fetchAccount(token: String): RefreshResult = call(
        onNoConnection = RefreshResult.NoConnection,
        onFailed = RefreshResult.Failed,
    ) {
        val response = client.get("/account/my_data", token)
        when (response.status) {
            200 -> RefreshResult.Success(parseAccount(response))
            401 -> RefreshResult.SignedOut
            else -> RefreshResult.Failed
        }
    }

    private fun parseAccount(response: ApiResponse): AccountData {
        val json = response.json()
        return AccountData(
            email = json.optNullableString("email"),
            phone = json.optNullableString("phone"),
            confirmed = json.optBoolean("confirmed", false),
            firstName = json.optString("firstName"),
            lastName = json.optString("lastName"),
            birthDate = json.optNullableString("birthDate")?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
            gender = parseGender(json.optJSONObject("gender")),
        )
    }

    /** { gender: "male" | "female", confidence: 0–100 }; null if missing or unrecognised. */
    private fun parseGender(json: JSONObject?): GenderGuess? {
        val gender = when (json?.optString("gender")?.lowercase()) {
            "male" -> Gender.Male
            "female" -> Gender.Female
            else -> return null
        }
        return GenderGuess(gender, json.optInt("confidence", 0).coerceIn(0, 100))
    }

    private fun JSONObject.optNullableString(name: String): String? =
        if (isNull(name)) null else optString(name).takeIf { it.isNotBlank() }

    /** Runs a request, mapping "no response" and "unreadable response" to the given results. */
    private inline fun <T> call(onNoConnection: T, onFailed: T, request: () -> T): T = try {
        request()
    } catch (_: IOException) {
        onNoConnection
    } catch (_: JSONException) {
        onFailed
    }
}
