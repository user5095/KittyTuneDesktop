package com.zionhuang.innertube.models.body

import com.zionhuang.innertube.models.Context
import kotlinx.serialization.Serializable

@Serializable
data class EditPlaylistBody(
    val context: Context,
    val playlistId: String,
    val actions: List<Action>,
) {
    @Serializable
    data class Action(
        val action: String,
        val addedVideoId: String? = null,
        val removedVideoId: String? = null,
        val setVideoId: String? = null,
    )
}

@Serializable
data class SubscribeBody(
    val context: Context,
    val channelIds: List<String>,
)
