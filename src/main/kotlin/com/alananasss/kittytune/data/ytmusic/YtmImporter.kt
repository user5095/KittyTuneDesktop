package com.alananasss.kittytune.data.ytmusic

import com.alananasss.kittytune.data.local.AppDatabase
import com.alananasss.kittytune.data.local.HistoryItem
import com.alananasss.kittytune.domain.Track
import com.alananasss.kittytune.domain.User
import com.zionhuang.innertube.YouTube
import com.zionhuang.innertube.models.SongItem
import kotlinx.coroutines.sync.withLock
import kotlin.math.abs

/**
 * Copies the logged-in YouTube Music account's library into the app: liked songs, followed artists and
 * listening history. Re-running is safe: ids are derived from the YouTube ids, so entries are updated,
 * not duplicated. (Playlist sync is not ported: the desktop local track table has no source/url columns.)
 */
object YtmImporter {
    enum class Step { LIKES, ARTISTS, HISTORY }

    data class Summary(val likes: Int, val artists: Int, val history: Int)

    // Same scheme the rest of the app uses for YouTube tracks (search results, radio).
    internal fun SongItem.toTrack() = Track(
        id = abs(id.hashCode().toLong()),
        title = title,
        user = User(0L, artists.joinToString(", ") { it.name }.ifBlank { "YouTube" }, null),
        artworkUrl = thumbnail,
        durationMs = (duration ?: 0) * 1000L,
        permalinkUrl = "https://youtube.com/watch?v=$id",
        source = "youtube"
    )

    /** Negative so an imported entry can never collide with a SoundCloud id. */
    internal fun localId(key: String) = -(abs(key.hashCode().toLong()) + 1)

    internal suspend fun allSongs(playlistId: String): List<SongItem> {
        val first = YouTube.playlistSongs(playlistId).getOrThrow()
        val songs = first.songs.toMutableList()
        var next = first.continuation
        while (next != null) {
            val page = YouTube.playlistContinuation(next).getOrThrow()
            songs += page.songs
            next = page.continuation
        }
        return songs
    }

    /** Full sync, one at a time: likes both ways, then artists and history pulled from YouTube Music. */
    suspend fun import(onStep: (Step) -> Unit): Summary =
        YtmSync.mutex.withLock { doImport(onStep).also { YtmSession.markSynced() } }

    private suspend fun doImport(onStep: (Step) -> Unit): Summary {
        val dao = AppDatabase.downloadDao
        val now = System.currentTimeMillis()

        onStep(Step.LIKES)
        val likes = YtmSync.syncLikes()

        onStep(Step.ARTISTS)
        val artists = YtmSync.syncArtists()

        onStep(Step.HISTORY)
        // OAuth sign-in (TV client) can't read the music-format history page: likes only.
        val history = if (YouTube.cookie == null) emptyList() else YouTube.history().getOrThrow()
        dao.insertHistoryList(history.mapIndexed { i, s ->
            val t = s.toTrack()
            HistoryItem(
                id = "track:${t.id}",
                numericId = t.id,
                title = s.title,
                subtitle = t.user?.username ?: "YouTube",
                imageUrl = s.thumbnail,
                type = "TRACK",
                timestamp = now - i * 1000L,
                source = "youtube",
                originalUrl = t.permalinkUrl
            )
        })

        return Summary(likes, artists, history.size)
    }
}
