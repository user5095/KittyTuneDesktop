package com.alananasss.kittytune.data.ytmusic

import com.alananasss.kittytune.data.LikeRepository
import com.alananasss.kittytune.data.local.AppDatabase
import com.alananasss.kittytune.utils.Logger
import com.alananasss.kittytune.data.local.LocalArtist
import com.alananasss.kittytune.domain.Track
import com.zionhuang.innertube.YouTube
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext

/**
 * Two-way like sync between the app and YouTube Music. YouTube tracks sync with YouTube Music and
 * SoundCloud tracks with SoundCloud (see LikeRepository); the app's library shows both.
 *
 * Three sets decide every change: what YouTube has now (remote), what the app has (local) and what
 * YouTube had at the last sync (synced). A like in remote-only is new on YouTube; one in synced but no
 * longer in remote was removed there; one in local only was made in the app and still has to be sent.
 */
object YtmSync {
    private const val MIN_INTERVAL_MS = 30 * 60 * 1000L
    // A sudden mass change is far more likely a bad read than the user's intent: do nothing instead.
    private const val MAX_BULK_CHANGE = 20
    private const val TAG = "YtmSync"

    val mutex = Mutex()

    private fun videoIdOf(url: String?): String? =
        url?.let { Regex("[?&]v=([\\w-]+)").find(it)?.groupValues?.get(1) }

    fun videoId(track: Track): String? = videoIdOf(track.permalinkUrl)

    /** The user liked or un-liked a YouTube track in the app: mirror it. A failure is repaired by the next [syncLikes]. */
    fun isYoutube(track: Track) = track.source == "youtube" || track.source == "youtube_music"

    /** The user liked or un-liked a YouTube track in the app: mirror it. A failure is repaired by the next [syncLikes]. */
    suspend fun pushLike(track: Track, like: Boolean) {
        if (!YtmSession.isLoggedIn()) return
        YtmSession.ensureFresh()
        val id = videoId(track) ?: return
        YouTube.likeVideo(id, like)
            .onSuccess {
                val synced = YtmSession.syncedLikes()
                YtmSession.setSyncedLikes(if (like) synced + id else synced - id)
            }
            .onFailure { Logger.w(TAG, "like push failed, will retry at next sync") }
    }

    /** Reconciles the liked songs in both directions. Returns how many songs YouTube Music has liked. */
    suspend fun syncLikes(): Int {
        YtmSession.ensureFresh()
        val remote = YtmImporter.allSongs("LM") // newest first
        val remoteIds = remote.mapTo(HashSet()) { it.id }
        val local = LikeRepository.likedTracks.value
            .filter { it.source == "youtube" || it.source == "youtube_music" }
            .mapNotNull { t -> videoId(t)?.let { it to t } }
            .toMap()
        val synced = YtmSession.syncedLikes()
        val now = System.currentTimeMillis()

        // YouTube -> app: liked there, unknown here. Ids in `synced` are excluded on purpose: those the
        // app already dropped and hasn't told YouTube about yet.
        val toAdd = remote.filter { it.id !in local && it.id !in synced }
        LikeRepository.applyRemoteLikesBatch(
            toAdd.mapIndexed { i, s -> with(YtmImporter) { s.toTrack() } to now - i * 1000L }
        )

        // YouTube -> app: un-liked there since the last sync. An empty answer with history is a bad read.
        val trustRemote = remote.isNotEmpty() || synced.isEmpty()
        val removedOnYoutube = if (trustRemote) local.filterKeys { it in synced && it !in remoteIds } else emptyMap()
        if (removedOnYoutube.size <= MAX_BULK_CHANGE) {
            LikeRepository.applyRemoteUnlikesBatch(removedOnYoutube.values.mapTo(HashSet()) { it.id })
        } else Logger.w(TAG, "skipped ${removedOnYoutube.size} local removals, looks like a bad read")

        // app -> YouTube: liked here, never seen there.
        val pushed = HashSet<String>()
        for (id in local.keys) {
            if (id in remoteIds || id in synced) continue
            if (YouTube.likeVideo(id, true).isSuccess) pushed += id
        }

        // app -> YouTube: un-liked here while it is still liked there.
        val unlikedHere = remoteIds.filter { it in synced && it !in local }
        val stillLiked = HashSet(remoteIds)
        if (unlikedHere.size <= MAX_BULK_CHANGE) {
            for (id in unlikedHere) if (YouTube.likeVideo(id, false).isSuccess) stillLiked -= id
        } else Logger.w(TAG, "skipped ${unlikedHere.size} remote un-likes, looks like a bad read")

        YtmSession.setSyncedLikes(stillLiked + pushed)
        return remote.size
    }

    /** Followed artists, both ways: following or unfollowing on either side is copied to the other. */
    suspend fun syncArtists(): Int {
        // OAuth sign-in only gets the TV client, whose library has no channel ids to follow/unfollow with.
        if (YouTube.cookie == null) return 0
        val dao = AppDatabase.downloadDao
        val remote = YouTube.libraryArtists().getOrThrow()
        val remoteIds = remote.mapTo(HashSet()) { it.id }
        val synced = YtmSession.syncedArtists()
        val local = dao.getAllSavedArtists().first().associateBy { it.id }
        fun localIdOf(channelId: String) = YtmImporter.localId("ytm-artist:$channelId")

        // YouTube -> app: followed there.
        dao.insertArtists(
            remote.filter { localIdOf(it.id) !in local && it.id !in synced }.map {
                LocalArtist(id = localIdOf(it.id), username = it.title, avatarUrl = it.thumbnail, trackCount = 0)
            }
        )

        // YouTube -> app: unfollowed there since the last sync.
        val trustRemote = remote.isNotEmpty() || synced.isEmpty()
        val removedOnYoutube = if (trustRemote) synced.filter { it !in remoteIds && localIdOf(it) in local } else emptyList()
        if (removedOnYoutube.size <= MAX_BULK_CHANGE) {
            removedOnYoutube.forEach { dao.deleteArtist(localIdOf(it)) }
        } else Logger.w(TAG, "skipped ${removedOnYoutube.size} artist removals, looks like a bad read")

        // app -> YouTube: unfollowed here while still followed there.
        val stillFollowed = HashSet(remoteIds)
        val unfollowedHere = remoteIds.filter { it in synced && localIdOf(it) !in local }
        if (unfollowedHere.size <= MAX_BULK_CHANGE) {
            for (cid in unfollowedHere) if (YouTube.subscribe(cid, false).isSuccess) stillFollowed -= cid
        } else Logger.w(TAG, "skipped ${unfollowedHere.size} unfollows, looks like a bad read")

        YtmSession.setSyncedArtists(stillFollowed)
        return remote.size
    }

    /** Runs a full sync when the app comes to the foreground and the last one is old enough. */
    suspend fun autoSync() {
        if (!YtmSession.isLoggedIn()) return
        if (System.currentTimeMillis() - YtmSession.lastSync() < MIN_INTERVAL_MS) return
        if (mutex.isLocked) return
        try {
            withContext(Dispatchers.IO) { YtmImporter.import() {} }
        } catch (e: Exception) {
            Logger.w(TAG, "auto sync failed: ${e.message}")
        }
    }
}
