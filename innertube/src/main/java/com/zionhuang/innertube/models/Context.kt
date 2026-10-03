package com.zionhuang.innertube.models

import kotlinx.serialization.Serializable

@Serializable
data class Context(
    val client: Client,
    val thirdParty: ThirdParty? = null,
) {
    @Serializable
    data class Client(
        val clientName: String,
        val clientVersion: String,
        val gl: String,
        val hl: String,
        val visitorData: String?,
        // YouTube's backend now rejects ANDROID/ANDROID_MUSIC /player requests with a 400
        // "FAILED_PRECONDITION" when this is missing, silently forcing every YouTube resolution
        // onto the much slower NewPipe HTML-scrape fallback (confirmed via on-device logcat).
        val androidSdkVersion: Int? = null,
        // The real Android/iOS apps always send this full block together (cross-checked against
        // yt-dlp's and YouTube.js's current, actively-maintained client configs); sending only
        // clientName/clientVersion/androidSdkVersion and leaving the rest out is itself a signal
        // the backend's bot/consistency checks can key on.
        val osName: String? = null,
        val osVersion: String? = null,
        val platform: String? = null,
        val deviceMake: String? = null,
        val deviceModel: String? = null,
        val timeZone: String? = null,
        val utcOffsetMinutes: Int? = null,
    )

    @Serializable
    data class ThirdParty(
        val embedUrl: String,
    )
}
