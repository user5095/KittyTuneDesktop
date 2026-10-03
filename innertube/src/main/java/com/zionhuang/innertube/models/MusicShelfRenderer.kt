package com.zionhuang.innertube.models

import kotlinx.serialization.Serializable

@Serializable
data class MusicShelfRenderer(
    val title: Runs?,
    val contents: List<Content>?,
    val bottomEndpoint: NavigationEndpoint?,
    val moreContentButton: Button?,
    val continuations: List<Continuation>?,
) {
    @Serializable
    data class Content(
        // Absent on the trailing item YouTube adds to long lists; that one carries the next page's token instead.
        val musicResponsiveListItemRenderer: MusicResponsiveListItemRenderer?,
        val continuationItemRenderer: ContinuationItemRenderer? = null,
    )

    @Serializable
    data class ContinuationItemRenderer(val continuationEndpoint: ContinuationEndpoint?) {
        @Serializable
        data class ContinuationEndpoint(val continuationCommand: ContinuationCommand?)

        @Serializable
        data class ContinuationCommand(val token: String?)
    }
}

fun List<Continuation>.getContinuation() =
    firstOrNull()?.nextContinuationData?.continuation

/** Next-page token of a long list whose continuation arrives as its last item rather than in `continuations`. */
fun List<MusicShelfRenderer.Content>.continuationFromItems() =
    lastOrNull()?.continuationItemRenderer?.continuationEndpoint?.continuationCommand?.token
