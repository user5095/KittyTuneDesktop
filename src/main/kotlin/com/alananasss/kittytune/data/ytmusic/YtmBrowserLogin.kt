package com.alananasss.kittytune.data.ytmusic

import com.alananasss.kittytune.core.AppDirs
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.io.File
import java.net.ServerSocket
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * Google sign-in for YouTube Music on the desktop. Google refuses embedded browsers, so a real Chromium-family
 * browser (Chrome, Chromium, Brave, Edge, Vivaldi) is started with a throwaway profile and a local debugging
 * port, the user signs in there as usual, and the session cookie is read back over the DevTools protocol.
 * The browser is closed and the profile deleted afterwards; nothing but the cookie is kept.
 */
object YtmBrowserLogin {
    private const val LOGIN_URL =
        "https://accounts.google.com/ServiceLogin?ltmpl=music&service=youtube&passive=true" +
            "&continue=https%3A%2F%2Fwww.youtube.com%2Fsignin%3Faction_handle_signin%3Dtrue%26next%3Dhttps%253A%252F%252Fmusic.youtube.com%252F"
    private const val TIMEOUT_MS = 5 * 60_000L

    class NoBrowserException : Exception("No Chromium-based browser found")

    private val client = OkHttpClient.Builder().readTimeout(0, TimeUnit.MILLISECONDS).pingInterval(20, TimeUnit.SECONDS).build()

    private fun findBrowser(): String? {
        val os = System.getProperty("os.name").lowercase()
        val names = listOf(
            "google-chrome-stable", "google-chrome", "chromium", "chromium-browser", "brave-browser", "brave",
            "microsoft-edge-stable", "microsoft-edge", "vivaldi", "vivaldi-stable", "chrome", "msedge",
        )
        val fixed = when {
            "win" in os -> listOfNotNull("PROGRAMFILES", "PROGRAMFILES(X86)", "LOCALAPPDATA").mapNotNull { System.getenv(it) }.flatMap { b ->
                listOf(
                    "$b\\Google\\Chrome\\Application\\chrome.exe", "$b\\Microsoft\\Edge\\Application\\msedge.exe",
                    "$b\\BraveSoftware\\Brave-Browser\\Application\\brave.exe", "$b\\Vivaldi\\Application\\vivaldi.exe",
                    "$b\\Chromium\\Application\\chrome.exe",
                )
            }
            "mac" in os -> listOf(
                "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome",
                "/Applications/Brave Browser.app/Contents/MacOS/Brave Browser",
                "/Applications/Microsoft Edge.app/Contents/MacOS/Microsoft Edge",
                "/Applications/Chromium.app/Contents/MacOS/Chromium",
                "/Applications/Vivaldi.app/Contents/MacOS/Vivaldi",
            )
            else -> emptyList()
        }
        fixed.firstOrNull { File(it).isFile }?.let { return it }
        val ext = if ("win" in os) ".exe" else ""
        val dirs = System.getenv("PATH").orEmpty().split(File.pathSeparator).filter { it.isNotBlank() }
        for (n in names) for (d in dirs) File(d, n + ext).takeIf { it.isFile && it.canExecute() }?.let { return it.absolutePath }
        return null
    }

    private class Cdp(url: String) {
        private val ids = AtomicInteger()
        private val pending = ConcurrentHashMap<Int, CompletableDeferred<JsonObject>>()
        private val ws: WebSocket = client.newWebSocket(Request.Builder().url(url).build(), object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket, text: String) {
                val o = runCatching { Json.parseToJsonElement(text).jsonObject }.getOrNull() ?: return
                o["id"]?.jsonPrimitive?.int?.let { pending.remove(it)?.complete(o) }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                pending.values.forEach { it.completeExceptionally(t) }
            }
        })

        suspend fun call(method: String): JsonObject? {
            val id = ids.incrementAndGet()
            val d = CompletableDeferred<JsonObject>().also { pending[id] = it }
            ws.send(buildJsonObject { put("id", id); put("method", method) }.toString())
            return withTimeoutOrNull(5_000) { runCatching { d.await() }.getOrNull() }.also { pending.remove(id) }
        }

        fun close() { ws.cancel() }
    }

    /** Cookies the browser would send to music.youtube.com, as a ready `Cookie` header, or null if not signed in yet. */
    private suspend fun readCookieHeader(cdp: Cdp): String? {
        val cookies = cdp.call("Storage.getCookies")?.get("result")?.jsonObject?.get("cookies")?.jsonArray ?: return null
        val mine = cookies.map { it.jsonObject }.filter {
            val d = it["domain"]?.jsonPrimitive?.contentOrNull?.trimStart('.') ?: return@filter false
            "music.youtube.com" == d || "music.youtube.com".endsWith(".$d") && d.endsWith("youtube.com")
        }
        if (mine.none { it["name"]?.jsonPrimitive?.contentOrNull == "SAPISID" }) return null
        return mine.joinToString("; ") { "${it["name"]!!.jsonPrimitive.content}=${it["value"]!!.jsonPrimitive.content}" }
    }

    /**
     * Opens the browser and waits for the user to sign in. Returns the cookie header, or null on timeout/cancel.
     * @throws NoBrowserException when no supported browser is installed.
     */
    suspend fun login(): String? = withContext(Dispatchers.IO) {
        val exe = findBrowser() ?: throw NoBrowserException()
        val profile = File(AppDirs.cacheDir, "ytm-login-profile").also { it.deleteRecursively(); it.mkdirs() }
        val port = ServerSocket(0).use { it.localPort }
        val proc = ProcessBuilder(
            exe, "--user-data-dir=${profile.absolutePath}", "--remote-debugging-port=$port",
            "--remote-allow-origins=*", "--no-first-run", "--no-default-browser-check", "--new-window", LOGIN_URL,
        ).redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD).start()
        var cdp: Cdp? = null
        try {
            val deadline = System.currentTimeMillis() + TIMEOUT_MS
            while (System.currentTimeMillis() < deadline) {
                delay(1500)
                if (!proc.isAlive) return@withContext null // the user closed the window
                if (cdp == null) {
                    val wsUrl = runCatching {
                        client.newCall(Request.Builder().url("http://127.0.0.1:$port/json/version").build()).execute().use { r ->
                            Json.parseToJsonElement(r.body.string()).jsonObject["webSocketDebuggerUrl"]?.jsonPrimitive?.contentOrNull
                        }
                    }.getOrNull() ?: continue
                    cdp = Cdp(wsUrl)
                }
                if (readCookieHeader(cdp) != null) {
                    delay(2500) // let the rest of the redirect chain finish setting cookies
                    return@withContext readCookieHeader(cdp)
                }
            }
            null
        } finally {
            runCatching { cdp?.call("Browser.close") }
            cdp?.close()
            if (!proc.waitFor(3, TimeUnit.SECONDS)) proc.destroy()
            runCatching { profile.deleteRecursively() }
        }
    }
}
