package com.zionhuang.innertube

import com.zionhuang.innertube.encoder.brotli
import com.zionhuang.innertube.models.Context
import com.zionhuang.innertube.models.YouTubeClient
import com.zionhuang.innertube.models.YouTubeLocale
import com.zionhuang.innertube.models.body.*
import com.zionhuang.innertube.utils.parseCookieString
import com.zionhuang.innertube.utils.sha1
import io.ktor.client.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.compression.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.util.encodeBase64
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import java.net.Proxy
import java.util.*

/**
 * Provide access to InnerTube endpoints.
 * For making HTTP requests, not parsing response.
 */
class InnerTube {
    private var httpClient = createClient()

    var locale = YouTubeLocale(
        gl = Locale.getDefault().country,
        hl = Locale.getDefault().toLanguageTag()
    )
    var visitorData: String = "CgtsZG1ySnZiQWtSbyiMjuGSBg%3D%3D"
    var cookie: String? = null
        set(value) {
            field = value
            cookieMap = if (value == null) emptyMap() else parseCookieString(value)
        }
    private var cookieMap = emptyMap<String, String>()

    /** OAuth bearer token (device-flow sign-in); used for signed-in requests when there is no cookie. */
    var accessToken: String? = null

    var proxy: Proxy? = null
        set(value) {
            field = value
            httpClient.close()
            httpClient = createClient()
        }

    @OptIn(ExperimentalSerializationApi::class)
    private fun createClient() = HttpClient(OkHttp) {
        expectSuccess = true

        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                explicitNulls = false
                encodeDefaults = true
            })
        }

        install(ContentEncoding) {
            brotli(1.0F)
            gzip(0.9F)
            deflate(0.8F)
        }

        if (proxy != null) {
            engine {
                proxy = this@InnerTube.proxy
            }
        }

        defaultRequest {
            url("https://music.youtube.com/youtubei/v1/")
        }
    }

    private fun HttpRequestBuilder.ytClient(client: YouTubeClient, setLogin: Boolean = false) {
        // The web API key belongs to another Google project than the OAuth token: sending both is a 400 INVALID_ARGUMENT.
        var useKey = true
        contentType(ContentType.Application.Json)
        headers {
            append("X-Goog-Api-Format-Version", "1")
            append("X-YouTube-Client-Name", client.clientName)
            append("X-YouTube-Client-Version", client.clientVersion)
            append("x-origin", "https://music.youtube.com")
            if (client.referer != null) {
                append("Referer", client.referer)
            }
            if (setLogin) {
                if (cookie == null) accessToken?.let { useKey = false; append("Authorization", "Bearer $it") }
                cookie?.let { cookie ->
                    append("cookie", cookie)
                    if ("SAPISID" !in cookieMap) return@let
                    val currentTime = System.currentTimeMillis() / 1000
                    val sapisidHash = sha1("$currentTime ${cookieMap["SAPISID"]} https://music.youtube.com")
                    append("Authorization", "SAPISIDHASH ${currentTime}_${sapisidHash}")
                }
            }
        }
        userAgent(client.userAgent)
        if (useKey) parameter("key", client.api_key)
        parameter("prettyPrint", false)
    }

    suspend fun search(
        client: YouTubeClient,
        query: String? = null,
        params: String? = null,
        continuation: String? = null,
    ) = httpClient.post("search") {
        ytClient(client)
        setBody(
            SearchBody(
                context = client.toContext(locale, visitorData),
                query = query,
                params = params
            )
        )
        parameter("continuation", continuation)
        parameter("ctoken", continuation)
    }

    suspend fun player(
        client: YouTubeClient,
        videoId: String,
        playlistId: String?,
    ) = httpClient.post("player") {
        // Public playback must stay unauthenticated. A YouTube Music SAPISID cookie can make
        // otherwise valid ANDROID player requests fail with INVALID_ARGUMENT; account cookies are
        // still used by browse/playlist endpoints below.
        ytClient(client)
        setBody(
            PlayerBody(
                context = client.toContext(locale, visitorData).let {
                    if (client == YouTubeClient.TVHTML5) {
                        it.copy(
                            thirdParty = Context.ThirdParty(
                                embedUrl = "https://www.youtube.com/watch?v=${videoId}"
                            )
                        )
                    } else it
                },
                videoId = videoId,
                playlistId = playlistId
            )
        )
    }

    suspend fun pipedStreams(videoId: String) =
        httpClient.get("https://pipedapi.kavin.rocks/streams/${videoId}") {
            contentType(ContentType.Application.Json)
        }

    suspend fun browse(
        client: YouTubeClient,
        browseId: String? = null,
        params: String? = null,
        continuation: String? = null,
        setLogin: Boolean = false,
    ) = httpClient.post("browse") {
        ytClient(client, setLogin)
        setBody(
            BrowseBody(
                context = client.toContext(locale, visitorData),
                browseId = browseId,
                params = params
            )
        )
        parameter("continuation", continuation)
        parameter("ctoken", continuation)
        if (continuation != null) {
            parameter("type", "next")
        }
    }

    suspend fun next(
        client: YouTubeClient,
        videoId: String?,
        playlistId: String?,
        playlistSetVideoId: String?,
        index: Int?,
        params: String?,
        continuation: String? = null,
    ) = httpClient.post("next") {
        ytClient(client, setLogin = true)
        setBody(
            NextBody(
                context = client.toContext(locale, visitorData),
                videoId = videoId,
                playlistId = playlistId,
                playlistSetVideoId = playlistSetVideoId,
                index = index,
                params = params,
                continuation = continuation
            )
        )
    }

    suspend fun getSearchSuggestions(
        client: YouTubeClient,
        input: String,
    ) = httpClient.post("music/get_search_suggestions") {
        ytClient(client)
        setBody(
            GetSearchSuggestionsBody(
                context = client.toContext(locale, visitorData),
                input = input
            )
        )
    }

    suspend fun getQueue(
        client: YouTubeClient,
        videoIds: List<String>?,
        playlistId: String?,
    ) = httpClient.post("music/get_queue") {
        ytClient(client)
        setBody(
            GetQueueBody(
                context = client.toContext(locale, visitorData),
                videoIds = videoIds,
                playlistId = playlistId
            )
        )
    }

    suspend fun getTranscript(
        client: YouTubeClient,
        videoId: String,
    ) = httpClient.post("https://music.youtube.com/youtubei/v1/get_transcript") {
        parameter("key", "AIzaSyC9XL3ZjWddXya6X74dJoCTL-WEYFDNX3")
        headers {
            append("Content-Type", "application/json")
        }
        setBody(
            GetTranscriptBody(
                context = client.toContext(locale, null),
                params = "\n${11.toChar()}$videoId".encodeBase64()
            )
        )
    }

    suspend fun getSwJsData() = httpClient.get("https://music.youtube.com/sw.js_data")

    suspend fun likeVideo(client: YouTubeClient, videoId: String, like: Boolean) =
        httpClient.post(if (like) "like/like" else "like/removelike") {
            ytClient(client, setLogin = true)
            setBody(LikeBody(client.toContext(locale, visitorData), LikeBody.Target(videoId)))
        }

    suspend fun editPlaylist(client: YouTubeClient, playlistId: String, actions: List<EditPlaylistBody.Action>) =
        httpClient.post("browse/edit_playlist") {
            ytClient(client, setLogin = true)
            setBody(EditPlaylistBody(client.toContext(locale, visitorData), playlistId, actions))
        }

    suspend fun subscribe(client: YouTubeClient, channelId: String, subscribe: Boolean) =
        httpClient.post(if (subscribe) "subscription/subscribe" else "subscription/unsubscribe") {
            ytClient(client, setLogin = true)
            setBody(SubscribeBody(client.toContext(locale, visitorData), listOf(channelId)))
        }

    suspend fun accountMenu(client: YouTubeClient) = httpClient.post("account/account_menu") {
        ytClient(client, setLogin = true)
        setBody(AccountMenuBody(client.toContext(locale, visitorData)))
    }
}
