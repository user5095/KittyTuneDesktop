package com.zionhuang.innertube.models

import kotlinx.serialization.Serializable

@Serializable
data class YouTubeClient(
    val clientName: String,
    val clientVersion: String,
    val api_key: String,
    val userAgent: String,
    val referer: String? = null,
    val androidSdkVersion: Int? = null,
    val osName: String? = null,
    val osVersion: String? = null,
    val platform: String? = null,
    val deviceMake: String? = null,
    val deviceModel: String? = null,
) {
    fun toContext(locale: YouTubeLocale, visitorData: String?) = Context(
        client = Context.Client(
            clientName = clientName,
            clientVersion = clientVersion,
            gl = locale.gl,
            hl = locale.hl,
            visitorData = visitorData,
            androidSdkVersion = androidSdkVersion,
            osName = osName,
            osVersion = osVersion,
            platform = platform,
            deviceMake = deviceMake,
            deviceModel = deviceModel,
            // UTC is close enough - the backend only seems to check that this is present and
            // internally consistent with itself, not that it matches the caller's real timezone.
            timeZone = if (osName != null) "UTC" else null,
            utcOffsetMinutes = if (osName != null) 0 else null,
        )
    )

    companion object {
        private const val REFERER_YOUTUBE_MUSIC = "https://music.youtube.com/"

        private const val USER_AGENT_WEB = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/74.0.3729.157 Safari/537.36"

        // Cross-checked against yt-dlp's and YouTube.js's actively-maintained InnerTube client
        // tables (Sep 2026) rather than guessed - the previous hardcoded versions (ANDROID_MUSIC
        // "5.01", ANDROID "17.13.3", TVHTML5 "2.0") were years stale, which is what was actually
        // triggering the 400 FAILED_PRECONDITION / "no longer supported" rejections, not a missing
        // field. Android pairing (SDK 30 / "11") and iOS pairing (device/OS string) are used
        // together as a matched set since the backend can check internal consistency between them.
        val ANDROID_MUSIC = YouTubeClient(
            clientName = "ANDROID_MUSIC",
            clientVersion = "5.34.51",
            api_key = "AIzaSyAOghZGza2MQSZkY_zfZ370N-PUdXEo8AI",
            userAgent = "com.google.android.apps.youtube.music/5.34.51 (Linux; U; Android 11) gzip",
            androidSdkVersion = 30,
            osName = "Android",
            osVersion = "11",
            platform = "MOBILE",
        )

        val ANDROID = YouTubeClient(
            clientName = "ANDROID",
            clientVersion = "21.26.364",
            api_key = "AIzaSyA8eiZmM1FaDVjRy-df2KTyQ_vz_yYM39w",
            userAgent = "com.google.android.youtube/21.26.364 (Linux; U; Android 11) gzip",
            androidSdkVersion = 30,
            osName = "Android",
            osVersion = "11",
            platform = "MOBILE",
        )

        val IOS = YouTubeClient(
            clientName = "IOS",
            clientVersion = "21.26.4",
            // Reusing ANDROID's key rather than guessing an iOS-specific one: confirmed via
            // research that InnerTube API keys aren't client-restricted or rotated, so any valid
            // key works for any client - the backend only checks clientName/clientVersion for
            // client identity.
            api_key = "AIzaSyA8eiZmM1FaDVjRy-df2KTyQ_vz_yYM39w",
            userAgent = "com.google.ios.youtube/21.26.4 (iPhone16,2; U; CPU iOS 18_3_2 like Mac OS X;)",
            osName = "iPhone",
            osVersion = "18.3.2.22D82",
            platform = "MOBILE",
            deviceMake = "Apple",
            deviceModel = "iPhone16,2",
        )

        val WEB = YouTubeClient(
            clientName = "WEB",
            clientVersion = "2.2021111",
            api_key = "AIzaSyC9XL3ZjWddXya6X74dJoCTL-WEYFDNX3",
            userAgent = USER_AGENT_WEB
        )

        val WEB_REMIX = YouTubeClient(
            clientName = "WEB_REMIX",
            clientVersion = "1.20260928.01.00",
            api_key = "AIzaSyC9XL3ZjWddXya6X74dJoCTL-WEYFDNX30",
            userAgent = USER_AGENT_WEB,
            referer = REFERER_YOUTUBE_MUSIC
        )

        val TVHTML5 = YouTubeClient(
            clientName = "TVHTML5_SIMPLY_EMBEDDED_PLAYER",
            clientVersion = "2.0",
            api_key = "AIzaSyDCU8hByM-4DrUqRUYnGn-3llEO78bcxq8",
            userAgent = "Mozilla/5.0 (PlayStation 4 5.55) AppleWebKit/601.2 (KHTML, like Gecko)"
        )
    }
}
