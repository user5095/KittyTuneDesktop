package com.alananasss.kittytune.data.ytmusic

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Google sign-in with the OAuth device flow ("limited input device", the one TVs use): the app shows a short
 * code and opens google.com/device in the user's default browser, the user signs in and confirms there, and the
 * app polls for the tokens. No cookies are involved and no particular browser is needed.
 *
 * The client id/secret are the public ones of the YouTube TV app, the same ones open-source YouTube Music
 * clients use for this flow; they identify the app, not the user.
 */
object YtmOAuth {
    private const val CLIENT_ID = "861556708454-d6dlm3lh05idd8npek18k6be8ba3oc68.apps.googleusercontent.com"
    private const val CLIENT_SECRET = "SboVhoG9s0rNafixCSGGKXAT"
    private const val SCOPE = "https://www.googleapis.com/auth/youtube"
    private val client = OkHttpClient()

    data class DeviceCode(val deviceCode: String, val userCode: String, val verificationUrl: String, val intervalSec: Int, val expiresInSec: Int)
    data class Tokens(val access: String, val refresh: String?, val expiresAtMs: Long)

    private fun post(url: String, vararg fields: Pair<String, String>) = Json.parseToJsonElement(
        client.newCall(
            Request.Builder().url(url).post(FormBody.Builder().apply { fields.forEach { add(it.first, it.second) } }.build()).build()
        ).execute().use { it.body.string() }
    ).jsonObject

    suspend fun start(): DeviceCode = withContext(Dispatchers.IO) {
        val o = post("https://oauth2.googleapis.com/device/code", "client_id" to CLIENT_ID, "scope" to SCOPE)
        DeviceCode(
            deviceCode = o["device_code"]?.jsonPrimitive?.contentOrNull ?: error("no device code: $o"),
            userCode = o["user_code"]!!.jsonPrimitive.content,
            verificationUrl = o["verification_url"]?.jsonPrimitive?.contentOrNull ?: "https://www.google.com/device",
            intervalSec = o["interval"]?.jsonPrimitive?.longOrNull?.toInt() ?: 5,
            expiresInSec = o["expires_in"]?.jsonPrimitive?.longOrNull?.toInt() ?: 1800,
        )
    }

    /** Polls until the user has approved the code (returns the tokens) or it expires/is denied (returns null). */
    suspend fun await(code: DeviceCode): Tokens? = withContext(Dispatchers.IO) {
        val deadline = System.currentTimeMillis() + code.expiresInSec * 1000L
        var interval = code.intervalSec
        while (System.currentTimeMillis() < deadline) {
            delay(interval * 1000L)
            val o = post(
                "https://oauth2.googleapis.com/token",
                "client_id" to CLIENT_ID, "client_secret" to CLIENT_SECRET,
                "device_code" to code.deviceCode, "grant_type" to "urn:ietf:params:oauth:grant-type:device_code",
            )
            o["access_token"]?.jsonPrimitive?.contentOrNull?.let { access ->
                return@withContext Tokens(access, o["refresh_token"]?.jsonPrimitive?.contentOrNull, System.currentTimeMillis() + (o["expires_in"]?.jsonPrimitive?.longOrNull ?: 3600) * 1000)
            }
            when (o["error"]?.jsonPrimitive?.contentOrNull) {
                "authorization_pending" -> {}
                "slow_down" -> interval += 5
                else -> return@withContext null // access_denied / expired_token
            }
        }
        null
    }

    suspend fun refresh(refreshToken: String): Tokens? = withContext(Dispatchers.IO) {
        val o = post(
            "https://oauth2.googleapis.com/token",
            "client_id" to CLIENT_ID, "client_secret" to CLIENT_SECRET, "refresh_token" to refreshToken, "grant_type" to "refresh_token",
        )
        val access = o["access_token"]?.jsonPrimitive?.contentOrNull ?: return@withContext null
        Tokens(access, refreshToken, System.currentTimeMillis() + (o["expires_in"]?.jsonPrimitive?.longOrNull ?: 3600) * 1000)
    }
}
